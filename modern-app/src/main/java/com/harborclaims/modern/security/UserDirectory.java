package com.harborclaims.modern.security;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads the shared user table. The role and the active flag always come from here, never from the cookie. Logins that were logged
 * out are listed in auth_revocations by their sid.
 */
@Repository
public class UserDirectory {

    public record User(String username, String role, boolean active) {
    }

    private final JdbcClient jdbc;

    public UserDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<User> find(String username) {
        return jdbc.sql("""
                SELECT u.username, r.name AS role_name, u.active
                FROM app_users u JOIN roles r ON r.id = u.role_id
                WHERE u.username = :username""")
                .param("username", username)
                .query((rs, n) -> new User(rs.getString("username"), rs.getString("role_name"), rs.getBoolean("active")))
                .optional();
    }

    /** True when a logout was recorded for this login. A primary-key lookup. */
    public boolean isRevoked(String sid) {
        Integer n = jdbc.sql("SELECT count(*) FROM auth_revocations WHERE sid = :sid").param("sid", sid).query(Integer.class).single();
        return n > 0;
    }
}
