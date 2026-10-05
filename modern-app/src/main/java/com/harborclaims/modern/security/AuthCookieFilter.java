package com.harborclaims.modern.security;

import com.harborclaims.modern.security.AuthCookieCodec.Claims;
import com.harborclaims.modern.security.UserDirectory.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns a valid auth-bridge cookie into an Authentication. Stateless: nothing is stored in an HTTP session. The cookie only
 * identifies the user; the role, the active flag and the logout time are read from the database on every request.
 */
public class AuthCookieFilter extends OncePerRequestFilter {

    public static final String CLAIMS_ATTRIBUTE = AuthCookieFilter.class.getName() + ".CLAIMS";
    private static final Set<String> ROLES = Set.of("ADMIN", "ADJUSTER", "SUPERVISOR", "UNDERWRITER", "CSR", "AUDITOR");

    private final AuthCookieCodec codec;
    private final UserDirectory users;
    private final Clock clock;
    private final boolean secureCookie;

    public AuthCookieFilter(AuthCookieCodec codec, UserDirectory users, Clock clock, boolean secureCookie) {
        this.codec = codec;
        this.users = users;
        this.clock = clock;
        this.secureCookie = secureCookie;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        authenticate(request, response);
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return;
        }
        for (Cookie cookie : cookies) {
            if (!AuthCookieCodec.COOKIE.equals(cookie.getName())) {
                continue;
            }
            Optional<Claims> claims = codec.verify(cookie.getValue());
            if (claims.isEmpty()) {
                return;
            }
            long now = clock.millis();
            Optional<User> user = users.find(claims.get().username());
            if (user.isEmpty() || !user.get().active() || !ROLES.contains(user.get().role())
                    || !AuthPolicy.acceptable(claims.get(), now) || users.isRevoked(claims.get().sid())) {
                return;
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.get().username(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.get().role()))));
            SecurityContextHolder.setContext(context);
            request.setAttribute(CLAIMS_ATTRIBUTE, claims.get());
            if (AuthPolicy.refreshNeeded(claims.get(), now)) {
                Claims fresh = AuthPolicy.refreshed(claims.get(), now);
                response.addHeader("Set-Cookie", cookieHeader(codec.sign(fresh), (fresh.expMillis() - now) / 1000, secureCookie));
            }
            return;
        }
    }

    /** Same name, path and attributes as the cookie legacy issues. */
    public static String cookieHeader(String value, long maxAgeSeconds, boolean secure) {
        return AuthCookieCodec.COOKIE + "=" + value + "; Path=/; Max-Age=" + maxAgeSeconds + "; HttpOnly; SameSite=Lax"
                + (secure ? "; Secure" : "");
    }
}
