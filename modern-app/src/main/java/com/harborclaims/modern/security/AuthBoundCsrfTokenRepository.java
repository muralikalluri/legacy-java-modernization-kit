package com.harborclaims.modern.security;

import com.harborclaims.modern.security.AuthCookieCodec.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

/**
 * The CSRF token is derived from the verified login (an HMAC over the username and the login id), not stored in a cookie
 * or a session. Cookies are not scoped by port on one host, so a cookie-based double submit could be forged by a sibling
 * app that plants cookies; this token cannot be computed without the key. It is stable across cookie refreshes.
 * A request with no verified login gets a random token that nothing will ever match.
 */
public class AuthBoundCsrfTokenRepository implements CsrfTokenRepository {

    private static final String HEADER = "X-CSRF-TOKEN";
    private static final String PARAMETER = "_csrf";

    private final AuthCookieCodec codec;

    public AuthBoundCsrfTokenRepository(AuthCookieCodec codec) {
        this.codec = codec;
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        CsrfToken bound = loadToken(request);
        return bound != null ? bound : new DefaultCsrfToken(HEADER, PARAMETER, UUID.randomUUID().toString());
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        // nothing to store: the token is recomputed from the verified login on every request
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        Object claims = request.getAttribute(AuthCookieFilter.CLAIMS_ATTRIBUTE);
        if (claims instanceof Claims c) {
            return new DefaultCsrfToken(HEADER, PARAMETER, codec.csrfToken(c.username(), c.sid()));
        }
        return null;
    }
}
