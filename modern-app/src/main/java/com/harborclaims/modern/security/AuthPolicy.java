package com.harborclaims.modern.security;

import com.harborclaims.modern.security.AuthCookieCodec.Claims;

/** The time and revocation rules for a verified cookie, as pure functions of the clock. */
public final class AuthPolicy {

    public static final long IDLE_MILLIS = 30L * 60 * 1000;
    public static final long CAP_MILLIS = 8L * 60 * 60 * 1000;
    public static final long SKEW_MILLIS = 60L * 1000;

    private AuthPolicy() {
    }

    /**
     * A cookie is acceptable while it has not expired, was not issued in the future (beyond a small clock skew), is within the
     * lifetime cap counted from the login. Whether the login was logged out is a database question, asked separately.
     */
    public static boolean acceptable(Claims c, long nowMillis) {
        return nowMillis < c.expMillis()
                && c.iatMillis() <= nowMillis + SKEW_MILLIS
                && c.authMillis() <= nowMillis + SKEW_MILLIS
                && nowMillis - c.authMillis() <= CAP_MILLIS;
    }

    /** Refresh when less than half the idle window is left and the lifetime cap has not been reached. */
    public static boolean refreshNeeded(Claims c, long nowMillis) {
        return c.expMillis() - nowMillis < IDLE_MILLIS / 2 && nowMillis < c.authMillis() + CAP_MILLIS;
    }

    /** The refreshed claims: same login time, a new idle window, never beyond the cap. */
    public static Claims refreshed(Claims c, long nowMillis) {
        long exp = Math.min(nowMillis + IDLE_MILLIS, c.authMillis() + CAP_MILLIS);
        return new Claims(c.username(), c.sid(), c.authMillis(), nowMillis, exp);
    }
}
