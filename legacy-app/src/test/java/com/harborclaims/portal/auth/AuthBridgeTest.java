package com.harborclaims.portal.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.junit.Test;

public class AuthBridgeTest {

    /** The same constants appear in modern-app's AuthCookieTest. They were produced by an independent HMAC implementation. */
    static final String SECRET = "test-secret-0123456789-abcdefghijkl";
    static final String VECTOR_TOKEN = "v1.eyJ1IjoiY3NyMSIsInNpZCI6IjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmIiwiYXV0aCI6MTcwMDAwMDAwMDAwMCwiaWF0IjoxNzAwMDAwMDAwMDAwLCJleHAiOjE3MDAwMDE4MDAwMDB9.UU6bV1LDdb-e6VWhIUVWtz24gPnZxgqQsEqrTeLSkKE";
    static final String VECTOR_TOKEN_ESCAPED = "v1.eyJ1IjoieFwiLFwiclwiOlwiQURNSU4iLCJzaWQiOiIwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZiIsImF1dGgiOjE3MDAwMDAwMDAwMDAsImlhdCI6MTcwMDAwMDAwMDAwMCwiZXhwIjoxNzAwMDAxODAwMDAwfQ.m84qzRax6ai4CVkR3DU09qqofZIvKg1VYd2ocBMOH_0";
    static final String SID = "0123456789abcdef0123456789abcdef";

    private static byte[] authKey() {
        return AuthBridge.deriveKey(SECRET.getBytes(StandardCharsets.UTF_8), "harbor-auth-cookie-v1");
    }

    @Test
    public void producesTheSharedTestVector() {
        assertEquals(VECTOR_TOKEN, AuthBridge.token(authKey(), "csr1", SID, 1700000000000L, 1700000000000L, 1700001800000L));
    }

    @Test
    public void aNameThatTriesToAddAJsonKeyIsEscapedNotInterpreted() {
        String token = AuthBridge.token(authKey(), "x\",\"r\":\"ADMIN", SID, 1700000000000L, 1700000000000L, 1700001800000L);
        assertEquals(VECTOR_TOKEN_ESCAPED, token);
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertTrue(payload, payload.startsWith("{\"u\":\"x\\\",\\\"r\\\":\\\"ADMIN\",\"sid\":"));
        assertFalse("exactly one u key and no r key outside the escaped name", payload.replace("\\\"r\\\"", "").contains("\"r\":"));
    }

    @Test
    public void controlAndNonAsciiCharactersAreEscaped() {
        assertEquals("a\\u0000b\\u00e9\\\\", AuthBridge.jsonEscape("a\u0000bé\\"));
        assertEquals("plain.name-1_x", AuthBridge.jsonEscape("plain.name-1_x"));
    }

    @Test
    public void everyLoginGetsItsOwnRandomSid() {
        String a = AuthBridge.newSid();
        String b = AuthBridge.newSid();
        assertFalse(a.equals(b));
        assertTrue(a.matches("[0-9a-f]{32}"));
    }

    @Test
    public void keysForDifferentPurposesDiffer() {
        byte[] secret = SECRET.getBytes(StandardCharsets.UTF_8);
        assertFalse(java.util.Arrays.equals(AuthBridge.deriveKey(secret, "harbor-auth-cookie-v1"), AuthBridge.deriveKey(secret, "harbor-csrf-v1")));
    }

    @Test
    public void issuedAndClearingCookiesHaveTheSameNamePathAndAttributes() {
        String issued = AuthBridge.cookieHeader("v1.x.y", 1800, false);
        String cleared = AuthBridge.cookieHeader("", 0, false);
        assertEquals("HARBOR_AUTH=v1.x.y; Path=/; Max-Age=1800; HttpOnly; SameSite=Lax", issued);
        assertEquals("HARBOR_AUTH=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax", cleared);
        assertFalse("no Domain attribute: it would stop the clearing cookie from matching", issued.contains("Domain"));
        assertTrue(AuthBridge.cookieHeader("v", 1, true).endsWith("; Secure"));
    }

    @Test
    public void withoutASecretNothingIsIssuedAndLegacyKeepsWorking() {
        // the test JVM has no HARBOR_AUTH_SECRET, so the bridge is disabled: login must not break
        List<String> headers = new ArrayList<String>();
        HttpServletResponse response = recording(headers);
        assertFalse(AuthBridge.enabled());
        assertEquals(-1, AuthBridge.issue(response, "csr1", SID, 0, 0));
        assertTrue(headers.isEmpty());
    }

    @Test
    public void clearingAlwaysWritesTheCookieHeader() {
        List<String> headers = new ArrayList<String>();
        AuthBridge.clear(recording(headers));
        assertEquals(1, headers.size());
        assertTrue(headers.get(0).startsWith("HARBOR_AUTH=; Path=/; Max-Age=0"));
    }

    private static HttpServletResponse recording(final List<String> headers) {
        return (HttpServletResponse) Proxy.newProxyInstance(AuthBridgeTest.class.getClassLoader(),
                new Class<?>[] {HttpServletResponse.class}, new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if (method.getName().equals("addHeader")) {
                            headers.add((String) args[1]);
                        }
                        return null;
                    }
                });
    }
}
