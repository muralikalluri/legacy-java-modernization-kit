package com.harborclaims.portal.auth;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/** Homegrown login filter: no session user means redirect to the login page. */
public class LoginFilter implements Filter {

    public static final String SESSION_USER = "currentUser";
    public static final String AUTH_TIME = "authTime";
    public static final String AUTH_SID = "authSid";
    public static final String COOKIE_EXP = "authCookieExp";

    public void init(FilterConfig config) {
    }

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String path = request.getServletPath();

        if (isPublic(path)) {
            chain.doFilter(req, res);
            return;
        }
        HttpSession session = request.getSession(false);
        AppUser user = session == null ? null : (AppUser) session.getAttribute(SESSION_USER);
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login.do");
            return;
        }
        refreshBridgeCookie(session, user, response);
        chain.doFilter(new RoleRequest(request, user), res);
    }

    /** Keeps the bridge cookie alive while the legacy session is in use, until the lifetime cap from the login time. */
    private void refreshBridgeCookie(HttpSession session, AppUser user, HttpServletResponse response) {
        Long auth = (Long) session.getAttribute(AUTH_TIME);
        String sid = (String) session.getAttribute(AUTH_SID);
        if (auth == null || sid == null || !AuthBridge.enabled()) {
            return;
        }
        Long exp = (Long) session.getAttribute(COOKIE_EXP);
        long now = System.currentTimeMillis();
        if (exp == null || exp.longValue() - now < AuthBridge.IDLE_MILLIS / 2) {
            long fresh = AuthBridge.issue(response, user.getUsername(), sid, auth.longValue(), now);
            if (fresh > 0) {
                session.setAttribute(COOKIE_EXP, Long.valueOf(fresh));
            }
        }
    }

    private boolean isPublic(String path) {
        return path.equals("/login.do") || path.equals("/loginSubmit.do") || path.equals("/logout.do")
                || path.startsWith("/css/") || path.equals("/error.jsp");
    }

    public void destroy() {
    }

    /** Lets Struts roles= attributes read the single role held in the session. */
    private static class RoleRequest extends HttpServletRequestWrapper {

        private final AppUser user;

        RoleRequest(HttpServletRequest request, AppUser user) {
            super(request);
            this.user = user;
        }

        @Override
        public boolean isUserInRole(String role) {
            return user.getRole().equals(role);
        }

        @Override
        public String getRemoteUser() {
            return user.getUsername();
        }
    }
}
