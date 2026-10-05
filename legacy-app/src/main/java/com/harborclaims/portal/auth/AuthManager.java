package com.harborclaims.portal.auth;

import java.sql.SQLException;

/** Homegrown authentication. Deliberately insecure for demonstration. Do not deploy. */
public final class AuthManager {

    private static final AuthManager INSTANCE = new AuthManager();

    private final UserDao userDao = new UserDao();

    private AuthManager() {
    }

    public static AuthManager getInstance() {
        return INSTANCE;
    }

    /** Returns the user, or null for unknown user, inactive user or wrong password. */
    public AppUser authenticate(String username, String password) throws SQLException {
        if (username == null || password == null) {
            return null;
        }
        AppUser user = userDao.findByUsername(username.trim());
        if (user == null || !user.isActive()) {
            return null;
        }
        // SEEDED FLAW: plaintext password comparison.
        if (!user.getPassword().equals(password)) {
            return null;
        }
        return user;
    }
}
