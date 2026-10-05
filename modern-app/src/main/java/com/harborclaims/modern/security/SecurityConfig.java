package com.harborclaims.modern.security;

import com.harborclaims.modern.config.HarborProperties;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * Spring Security 6 for the migrated routes. Stateless (no HTTP session), CSRF on for every state-changing request, roles
 * enforced in the filter chain so they are checked before any controller runs (and before an id is looked up or a form is
 * validated), plus method security on the service layer as a second line.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(HarborProperties.class)
public class SecurityConfig {

    static final String LOGIN_PATH = "/login.do";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AuthCookieCodec authCookieCodec(HarborProperties props) {
        return AuthCookieCodec.of(props.secret(), props.previousSecret(), props.allowPlaceholderSecret());
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AuthCookieCodec codec, UserDirectory users, Clock clock, HarborProperties props)
            throws Exception {
        AuthCookieFilter cookieFilter = new AuthCookieFilter(codec, users, clock, props.cookieSecure());
        http
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(rc -> rc.disable())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf.csrfTokenRepository(new AuthBoundCsrfTokenRepository(codec)))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(loginRedirect()).accessDeniedHandler(deniedHandler()))
                .authorizeHttpRequests(a -> a
                        // error and forward dispatches must never be turned into login redirects (a 404 must stay a 404)
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/customers/edit.do", "/customers/save.do").hasAnyRole("ADMIN", "SUPERVISOR", "CSR")
                        .requestMatchers("/customers/delete.do").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(cookieFilter, CsrfFilter.class);
        return http.build();
    }

    /** Sends the caller to the legacy login page with a relative Location and no query, as legacy does. */
    static void redirectToLogin(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", LOGIN_PATH);
    }

    AuthenticationEntryPoint loginRedirect() {
        return (HttpServletRequest request, HttpServletResponse response, org.springframework.security.core.AuthenticationException e) ->
                redirectToLogin(response);
    }

    /**
     * Anonymous callers (including a POST whose CSRF check failed, which runs before anonymous authentication) go to the login
     * page. Signed-in callers get 403. The status is set directly: sendError would trigger an error dispatch.
     */
    AccessDeniedHandler deniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response, org.springframework.security.access.AccessDeniedException e)
                -> {
            if (isAnonymous(SecurityContextHolder.getContext().getAuthentication())) {
                redirectToLogin(response);
            } else {
                forbidden(response);
            }
        };
    }

    static boolean isAnonymous(Authentication a) {
        return a == null || a instanceof AnonymousAuthenticationToken || !a.isAuthenticated();
    }

    private static void forbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/plain;charset=UTF-8");
        response.getOutputStream().write("Access denied".getBytes(StandardCharsets.UTF_8));
    }
}
