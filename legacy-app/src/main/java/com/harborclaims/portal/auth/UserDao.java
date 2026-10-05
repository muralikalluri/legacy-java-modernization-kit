package com.harborclaims.portal.auth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.harborclaims.portal.common.Db;

public class UserDao {

    public AppUser findByUsername(String username) throws SQLException {
        String sql = "SELECT u.id, u.username, u.password, u.display_name, u.active, r.name AS role_name "
                + "FROM app_users u JOIN roles r ON r.id = u.role_id WHERE u.username = ?";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                AppUser u = new AppUser();
                u.setId(rs.getInt("id"));
                u.setUsername(rs.getString("username"));
                u.setPassword(rs.getString("password"));
                u.setDisplayName(rs.getString("display_name"));
                u.setActive(rs.getBoolean("active"));
                u.setRole(rs.getString("role_name"));
                return u;
            }
        }
    }
}
