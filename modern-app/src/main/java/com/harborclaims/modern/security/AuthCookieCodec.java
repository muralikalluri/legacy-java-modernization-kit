package com.harborclaims.modern.security;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies and signs the auth-bridge cookie: {@code v1.<base64url(payload json)>.<base64url(HMAC-SHA256 over "v1.<payload>")>}.
 * The cookie says who signed in and when. It never carries a role: the role comes from the database on every request.
 * Legacy issues the cookie; this class can also sign (to refresh it) and derive the CSRF token for a login.
 */
public final class AuthCookieCodec {

    /**
     * Who signed in, which login this is (a random id, so a logout can revoke exactly this login), when that login happened and
     * the issue and expiry times, all in milliseconds since the epoch.
     */
    public record Claims(String username, String sid, long authMillis, long iatMillis, long expMillis) {
    }

    private record Payload(String u, String sid, Long auth, Long iat, Long exp) {
    }

    public static final String COOKIE = "HARBOR_AUTH";
    public static final String VERSION = "v1";
    public static final int MIN_SECRET_BYTES = 32;
    static final int MAX_TOKEN_CHARS = 1024;
    private static final Pattern BASE64URL = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{1,40}");
    private static final Pattern SID = Pattern.compile("[0-9a-f]{32}");

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final byte[] authKey;
    private final byte[] previousAuthKey;
    private final byte[] csrfKey;

    private AuthCookieCodec(byte[] authKey, byte[] previousAuthKey, byte[] csrfKey) {
        this.authKey = authKey;
        this.previousAuthKey = previousAuthKey;
        this.csrfKey = csrfKey;
    }

    /** Fails fast: a missing, short or placeholder secret must stop the app from starting. */
    public static AuthCookieCodec of(String secret, String previousSecret, boolean allowPlaceholder) {
        requireStrong("HARBOR_AUTH_SECRET", secret, allowPlaceholder);
        byte[] current = secret.getBytes(StandardCharsets.UTF_8);
        byte[] previous = null;
        if (previousSecret != null && !previousSecret.isBlank()) {
            requireStrong("HARBOR_AUTH_SECRET_PREVIOUS", previousSecret, allowPlaceholder);
            previous = derive(previousSecret.getBytes(StandardCharsets.UTF_8), "harbor-auth-cookie-v1");
        }
        return new AuthCookieCodec(derive(current, "harbor-auth-cookie-v1"), previous, derive(current, "harbor-csrf-v1"));
    }

    private static void requireStrong(String name, String secret, boolean allowPlaceholder) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(name + " must be set and at least " + MIN_SECRET_BYTES + " bytes long");
        }
        if (!allowPlaceholder && secret.startsWith("change-me")) {
            throw new IllegalStateException(name + " is still the placeholder from .env.example; set a real secret "
                    + "(or set HARBOR_AUTH_ALLOW_PLACEHOLDER=true for a local demo only)");
        }
    }

    /** One secret gives separate keys per purpose: HMAC(secret, label). */
    static byte[] derive(byte[] secret, String label) {
        return hmac(secret, label.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    public String sign(Claims c) {
        return signRawPayload("{\"u\":" + jsonString(c.username()) + ",\"sid\":" + jsonString(c.sid()) + ",\"auth\":" + c.authMillis() + ",\"iat\":" + c.iatMillis()
                + ",\"exp\":" + c.expMillis() + "}");
    }

    /** Signs an arbitrary payload string. Package-private so tests can build hostile but correctly signed tokens. */
    String signRawPayload(String payload) {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String signedPart = VERSION + "." + b64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return signedPart + "." + b64.encodeToString(hmac(authKey, signedPart.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Checks shape and signature, then parses the payload. Time rules are not checked here (see AuthPolicy).
     * The MAC is verified over the exact bytes received, before any JSON is parsed.
     */
    public Optional<Claims> verify(String token) {
        if (token == null || token.length() > MAX_TOKEN_CHARS) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0]) || !BASE64URL.matcher(parts[1]).matches()
                || !BASE64URL.matcher(parts[2]).matches()) {
            return Optional.empty();
        }
        byte[] given;
        byte[] payloadBytes;
        try {
            given = Base64.getUrlDecoder().decode(parts[2]);
            payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        byte[] signed = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
        boolean ok = MessageDigest.isEqual(given, hmac(authKey, signed))
                | (previousAuthKey != null && MessageDigest.isEqual(given, hmac(previousAuthKey, signed)));
        if (!ok) {
            return Optional.empty();
        }
        try {
            Payload p = JSON.readValue(payloadBytes, Payload.class);
            if (p.u() == null || p.sid() == null || p.auth() == null || p.iat() == null || p.exp() == null
                    || !USERNAME.matcher(p.u()).matches() || !SID.matcher(p.sid()).matches()) {
                return Optional.empty();
            }
            return Optional.of(new Claims(p.u(), p.sid(), p.auth(), p.iat(), p.exp()));
        } catch (java.io.IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * The anti-CSRF token for one login: an HMAC over the username and the login id. It is stable while the cookie is
     * refreshed, and an attacker who can plant cookies cannot compute it without the key.
     */
    public String csrfToken(String username, String sid) {
        String subject = username.length() + ":" + username + ":" + sid;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(csrfKey, subject.getBytes(StandardCharsets.UTF_8)));
    }

    private static String jsonString(String s) {
        try {
            return JSON.writeValueAsString(s);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
