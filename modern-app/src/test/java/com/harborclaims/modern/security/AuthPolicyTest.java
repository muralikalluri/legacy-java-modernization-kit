package com.harborclaims.modern.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.harborclaims.modern.security.AuthCookieCodec.Claims;
import org.junit.jupiter.api.Test;

class AuthPolicyTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long MIN = 60_000L;

    private static Claims at(long auth, long iat, long exp) {
        return new Claims("csr1", "0123456789abcdef0123456789abcdef", auth, iat, exp);
    }

    @Test
    void aFreshCookieIsAcceptable() {
        assertTrue(AuthPolicy.acceptable(at(NOW, NOW, NOW + 30 * MIN), NOW));
    }

    @Test
    void expiryIsStrict() {
        assertTrue(AuthPolicy.acceptable(at(NOW - MIN, NOW - MIN, NOW + 1), NOW));
        assertFalse(AuthPolicy.acceptable(at(NOW - MIN, NOW - MIN, NOW), NOW), "exp equal to now is expired");
        assertFalse(AuthPolicy.acceptable(at(NOW - MIN, NOW - MIN, NOW - 1), NOW));
    }

    @Test
    void aCookieIssuedInTheFutureIsRefusedBeyondTheClockSkew() {
        assertTrue(AuthPolicy.acceptable(at(NOW, NOW + AuthPolicy.SKEW_MILLIS, NOW + 30 * MIN), NOW));
        assertFalse(AuthPolicy.acceptable(at(NOW, NOW + AuthPolicy.SKEW_MILLIS + 1, NOW + 30 * MIN), NOW));
        assertFalse(AuthPolicy.acceptable(at(NOW + AuthPolicy.SKEW_MILLIS + 1, NOW, NOW + 30 * MIN), NOW));
    }

    @Test
    void theLifetimeCapCountsFromTheLoginNotFromTheLastRefresh() {
        long auth = NOW - AuthPolicy.CAP_MILLIS;
        assertTrue(AuthPolicy.acceptable(at(auth, NOW, NOW + 10 * MIN), NOW), "exactly at the cap");
        assertFalse(AuthPolicy.acceptable(at(auth - 1, NOW, NOW + 10 * MIN), NOW), "one millisecond past the cap, however fresh the cookie");
    }

    @Test
    void refreshHappensOnlyInTheSecondHalfOfTheIdleWindow() {
        assertFalse(AuthPolicy.refreshNeeded(at(NOW, NOW, NOW + AuthPolicy.IDLE_MILLIS), NOW));
        assertFalse(AuthPolicy.refreshNeeded(at(NOW, NOW, NOW + AuthPolicy.IDLE_MILLIS / 2), NOW));
        assertTrue(AuthPolicy.refreshNeeded(at(NOW, NOW, NOW + AuthPolicy.IDLE_MILLIS / 2 - 1), NOW));
    }

    @Test
    void refreshKeepsTheLoginIdentity() {
        Claims fresh = AuthPolicy.refreshed(at(NOW - 20 * MIN, NOW - 20 * MIN, NOW + MIN), NOW);
        assertEquals("0123456789abcdef0123456789abcdef", fresh.sid(), "the sid must survive a refresh, or a logout could not revoke the login");
    }

    @Test
    void refreshKeepsTheLoginTimeAndNeverPassesTheCap() {
        long auth = NOW - AuthPolicy.CAP_MILLIS + 5 * MIN;
        Claims fresh = AuthPolicy.refreshed(at(auth, NOW - 20 * MIN, NOW + MIN), NOW);
        assertEquals(auth, fresh.authMillis(), "a refresh must not move the login time, or a logout could not revoke it");
        assertEquals(auth + AuthPolicy.CAP_MILLIS, fresh.expMillis(), "capped at login + the lifetime cap");
        assertEquals(NOW, fresh.iatMillis());
        Claims normal = AuthPolicy.refreshed(at(NOW - 20 * MIN, NOW - 20 * MIN, NOW + MIN), NOW);
        assertEquals(NOW + AuthPolicy.IDLE_MILLIS, normal.expMillis());
    }

    @Test
    void noRefreshOnceTheCapIsReached() {
        assertFalse(AuthPolicy.refreshNeeded(at(NOW - AuthPolicy.CAP_MILLIS, NOW - 25 * MIN, NOW + MIN), NOW));
    }
}
