package com.harborclaims.modern.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.harborclaims.modern.security.AuthCookieCodec.Claims;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuthCookieCodecTest {

    /** Produced by an independent HMAC implementation; the same constants are in the legacy AuthBridgeTest. */
    static final String SECRET = "test-secret-0123456789-abcdefghijkl";
    static final String VECTOR = "v1.eyJ1IjoiY3NyMSIsInNpZCI6IjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmIiwiYXV0aCI6MTcwMDAwMDAwMDAwMCwiaWF0IjoxNzAwMDAwMDAwMDAwLCJleHAiOjE3MDAwMDE4MDAwMDB9.UU6bV1LDdb-e6VWhIUVWtz24gPnZxgqQsEqrTeLSkKE";
    static final String VECTOR_ESCAPED = "v1.eyJ1IjoieFwiLFwiclwiOlwiQURNSU4iLCJzaWQiOiIwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZiIsImF1dGgiOjE3MDAwMDAwMDAwMDAsImlhdCI6MTcwMDAwMDAwMDAwMCwiZXhwIjoxNzAwMDAxODAwMDAwfQ.m84qzRax6ai4CVkR3DU09qqofZIvKg1VYd2ocBMOH_0";
    static final String CSRF_VECTOR = "P_kEpitjDbUfTFOd_jUsaLtnWdpyswJ9yffUJAlgHN0";
    static final String SID = "0123456789abcdef0123456789abcdef";

    private final AuthCookieCodec codec = AuthCookieCodec.of(SECRET, "", false);
    private final Claims claims = new Claims("csr1", SID, 1700000000000L, 1700000000000L, 1700001800000L);

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void verifiesTheTokenLegacySigns() {
        assertEquals(Optional.of(claims), codec.verify(VECTOR));
    }

    @Test
    void signsTheSameBytesLegacyDoes() {
        assertEquals(VECTOR, codec.sign(claims));
    }

    @Test
    void theCsrfTokenForALoginMatchesTheIndependentVector() {
        assertEquals(CSRF_VECTOR, codec.csrfToken("csr1", SID));
    }

    @Test
    void theCsrfTokenIsBoundToTheLoginNotTheCookieValue() {
        String a = codec.csrfToken("csr1", SID);
        assertEquals(a, codec.csrfToken("csr1", SID), "stable while the cookie is refreshed");
        assertNotEquals(a, codec.csrfToken("csr1", "fedcba9876543210fedcba9876543210"), "a new login has a new token");
        assertNotEquals(a, codec.csrfToken("admin", SID), "and another user has another token");
    }

    @Test
    void aNameThatTriesToInjectARoleKeyDoesNotVerify() {
        // legacy escaped it correctly, so it is one key; the name is simply not a valid user name, so the token is refused
        assertEquals(Optional.empty(), codec.verify(VECTOR_ESCAPED));
    }

    @Test
    void roundTrips() {
        assertEquals(Optional.of(claims), codec.verify(codec.sign(claims)));
    }

    @Test
    void aTamperedPartIsRefused() {
        String[] p = VECTOR.split("\\.");
        assertEquals(Optional.empty(), codec.verify(p[0] + "." + b64("{\"u\":\"admin\",\"sid\":\"0123456789abcdef0123456789abcdef\",\"auth\":1700000000000,\"iat\":1700000000000,\"exp\":1700001800000}") + "." + p[2]));
        assertEquals(Optional.empty(), codec.verify(p[0] + "." + p[1] + "." + p[2].substring(0, p[2].length() - 1) + "A"));
        assertEquals(Optional.empty(), codec.verify(p[0] + "." + p[1] + "."));
    }

    @Test
    void theVersionMustBeTheLiteralV1() {
        String[] p = VECTOR.split("\\.");
        for (String v : new String[] {"v2", "V1", "v0", "", "v1 "}) {
            assertEquals(Optional.empty(), codec.verify(v + "." + p[1] + "." + p[2]), "version '" + v + "'");
        }
    }

    @Test
    void shapeProblemsAreRefusedBeforeAnyParsing() {
        assertEquals(Optional.empty(), codec.verify(null));
        assertEquals(Optional.empty(), codec.verify(""));
        assertEquals(Optional.empty(), codec.verify(VECTOR + ".extra"));
        assertEquals(Optional.empty(), codec.verify(VECTOR.substring(0, VECTOR.lastIndexOf('.'))));
        assertEquals(Optional.empty(), codec.verify(VECTOR + "="), "padding is not part of the format");
        assertEquals(Optional.empty(), codec.verify("v1.a b.c"));
        assertEquals(Optional.empty(), codec.verify("v1." + "A".repeat(2000) + ".AAAA"), "oversized tokens are refused");
    }

    @Test
    void aWrongSecretDoesNotVerify() {
        AuthCookieCodec other = AuthCookieCodec.of("a-completely-different-secret-0123456789", "", false);
        assertEquals(Optional.empty(), other.verify(VECTOR));
    }

    @Test
    void duplicateJsonKeysAreRefusedEvenWhenCorrectlySigned() {
        String hostile = codec.signRawPayload("{\"u\":\"csr1\",\"u\":\"admin\",\"sid\":\"0123456789abcdef0123456789abcdef\",\"auth\":1,\"iat\":1,\"exp\":2}");
        assertEquals(Optional.empty(), codec.verify(hostile));
    }

    @Test
    void unknownOrMissingFieldsAreRefused() {
        assertEquals(Optional.empty(), codec.verify(codec.signRawPayload("{\"u\":\"csr1\",\"r\":\"ADMIN\",\"sid\":\"0123456789abcdef0123456789abcdef\",\"auth\":1,\"iat\":1,\"exp\":2}")),
                "there is no role in the cookie, and an extra one is never read");
        assertEquals(Optional.empty(), codec.verify(codec.signRawPayload("{\"u\":\"csr1\",\"sid\":\"0123456789abcdef0123456789abcdef\",\"auth\":1,\"iat\":1}")));
        assertEquals(Optional.empty(), codec.verify(codec.signRawPayload("not json")));
        assertEquals(Optional.empty(), codec.verify(codec.signRawPayload("{\"u\":\"csr1\",\"sid\":\"0123456789abcdef0123456789abcdef\",\"auth\":1,\"iat\":1,\"exp\":2} trailing")));
    }

    @Test
    void userNamesOutsideTheAllowedAlphabetAreRefused() {
        for (String bad : new String[] {"", "a b", "a\"b", "a/b", "x".repeat(41), "café"}) {
            assertEquals(Optional.empty(), codec.verify(codec.sign(new Claims(bad, SID, 1, 1, 2))), "name '" + bad + "'");
        }
    }

    @Test
    void aMissingOrMalformedLoginIdIsRefused() {
        assertEquals(Optional.empty(), codec.verify(codec.signRawPayload("{\"u\":\"csr1\",\"auth\":1,\"iat\":1,\"exp\":2}")), "no sid");
        for (String bad : new String[] {"", "ABCDEF0123456789ABCDEF0123456789", "0123", "0123456789abcdef0123456789abcdeg", "0123456789abcdef0123456789abcdef0"}) {
            assertEquals(Optional.empty(), codec.verify(codec.sign(new Claims("csr1", bad, 1, 1, 2))), "sid '" + bad + "'");
        }
    }

    @Test
    void aPreviousSecretStillVerifiesDuringRotation() {
        AuthCookieCodec old = AuthCookieCodec.of("the-previous-secret-0123456789-abcdef", "", false);
        String issuedByOld = old.sign(claims);
        AuthCookieCodec rotated = AuthCookieCodec.of(SECRET, "the-previous-secret-0123456789-abcdef", false);
        assertEquals(Optional.of(claims), rotated.verify(issuedByOld));
        assertEquals(Optional.empty(), codec.verify(issuedByOld), "without the previous secret it is refused");
        assertFalse(rotated.sign(claims).equals(issuedByOld), "new tokens are signed with the current secret");
    }

    @Test
    void aWeakOrPlaceholderSecretStopsTheAppFromStarting() {
        assertThrows(IllegalStateException.class, () -> AuthCookieCodec.of(null, "", false));
        assertThrows(IllegalStateException.class, () -> AuthCookieCodec.of("", "", false));
        assertThrows(IllegalStateException.class, () -> AuthCookieCodec.of("too-short", "", false));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AuthCookieCodec.of("change-me-auth-secret-min-32-characters", "", false));
        assertTrue(e.getMessage().contains("placeholder"));
        AuthCookieCodec.of("change-me-auth-secret-min-32-characters", "", true);
        assertThrows(IllegalStateException.class, () -> AuthCookieCodec.of(SECRET, "short", false));
    }

    @Test
    void theByteLengthNotTheCharacterCountIsMeasured() {
        // 20 two-byte characters are 40 bytes, so this is accepted; 31 ASCII characters are 31 bytes, so this is refused
        AuthCookieCodec.of("é".repeat(20), "", false);
        assertThrows(IllegalStateException.class, () -> AuthCookieCodec.of("a".repeat(31), "", false));
    }
}
