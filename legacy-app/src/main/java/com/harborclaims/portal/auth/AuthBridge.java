package com.harborclaims.portal.auth;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

/**
 * The legacy half of the auth bridge. Legacy stays the sign-in authority and ISSUES a signed cookie that the modern app
 * verifies. The cookie says who signed in and when (never the role: the modern app reads that from the database).
 * Format: v1.&lt;base64url(payload json)&gt;.&lt;base64url(HMAC-SHA256 over "v1.&lt;payload&gt;")&gt;.
 * See docs/adr/0005-auth-bridge-and-strangler-gateway.md. Disabled (no cookie) when no secret of at least 32 bytes is configured.
 */
public final class AuthBridge {

    public static final String COOKIE = "HARBOR_AUTH";
    public static final long IDLE_MILLIS = 30L * 60 * 1000;
    public static final long CAP_MILLIS = 8L * 60 * 60 * 1000;
    public static final int MIN_SECRET_BYTES = 32;

    private static final Logger LOG = Logger.getLogger(AuthBridge.class);
    private static final byte[] KEY = loadKey();
    private static final boolean SECURE = "true".equalsIgnoreCase(System.getenv("HARBOR_COOKIE_SECURE"));

    private AuthBridge() {
    }

    private static byte[] loadKey() {
        String secret = System.getenv("HARBOR_AUTH_SECRET");
        if (secret == null || secret.length() == 0) {
            secret = System.getProperty("harbor.auth.secret");
        }
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            LOG.warn("Auth bridge disabled: HARBOR_AUTH_SECRET is missing or shorter than " + MIN_SECRET_BYTES + " bytes");
            return null;
        }
        return deriveKey(secret.getBytes(StandardCharsets.UTF_8), "harbor-auth-cookie-v1");
    }

    public static boolean enabled() {
        return KEY != null;
    }

    /** A random id for one login: 32 lower-case hex characters. */
    public static String newSid() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /** Separate keys per purpose come from one secret: HMAC(secret, label). */
    static byte[] deriveKey(byte[] secret, String label) {
        return hmac(secret, label.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The signed token for a user. Times are milliseconds since the epoch. */
    static String token(byte[] authKey, String username, String sid, long authMillis, long iatMillis, long expMillis) {
        String payload = "{\"u\":\"" + jsonEscape(username) + "\",\"sid\":\"" + jsonEscape(sid) + "\",\"auth\":" + authMillis + ",\"iat\":" + iatMillis
                + ",\"exp\":" + expMillis + "}";
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String signedPart = "v1." + b64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        byte[] sig = hmac(authKey, signedPart.getBytes(StandardCharsets.UTF_8));
        return signedPart + "." + b64.encodeToString(sig);
    }

    /** Escapes quotes, backslashes, control characters and everything outside printable ASCII, so a name can never add a JSON key. */
    static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                sb.append("\\\"");
            } else if (c == '\\') {
                sb.append("\\\\");
            } else if (c < 0x20 || c > 0x7e) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Sets the cookie. The expiry is the idle window from now, never beyond the lifetime cap counted from the login time.
     * Returns the expiry in milliseconds, or -1 when nothing was issued.
     */
    public static long issue(HttpServletResponse response, String username, String sid, long authMillis, long nowMillis) {
        if (KEY == null) {
            return -1;
        }
        long exp = Math.min(nowMillis + IDLE_MILLIS, authMillis + CAP_MILLIS);
        if (exp <= nowMillis) {
            return -1;
        }
        response.addHeader("Set-Cookie", cookieHeader(token(KEY, username, sid, authMillis, nowMillis, exp), (exp - nowMillis) / 1000, SECURE));
        return exp;
    }

    /** Clears the cookie with the same name, path and attributes it was set with, and no Domain. */
    public static void clear(HttpServletResponse response) {
        response.addHeader("Set-Cookie", cookieHeader("", 0, SECURE));
    }

    /** Servlet 3.0 has no SameSite on Cookie, so the header is written by hand. */
    static String cookieHeader(String value, long maxAgeSeconds, boolean secure) {
        return COOKIE + "=" + value + "; Path=/; Max-Age=" + maxAgeSeconds + "; HttpOnly; SameSite=Lax" + (secure ? "; Secure" : "");
    }

    /** Logout revokes this login only. Best effort: a logout must never fail because of this. */
    public static void revoke(String sid) {
        if (sid == null) {
            return;
        }
        try {
            new UserDao().revokeLogin(sid, System.currentTimeMillis(), CAP_MILLIS);
        } catch (java.sql.SQLException e) {
            LOG.warn("Could not record the logout of login " + sid + ": " + e.getMessage());
        }
    }
}
