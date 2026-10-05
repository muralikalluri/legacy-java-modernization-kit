package com.harborclaims.portal.admin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.harborclaims.portal.auth.AppUser;
import com.harborclaims.portal.common.Db;
import com.harborclaims.portal.common.Page;
import com.harborclaims.portal.common.Paging;

/** Deliberately insecure for demonstration. Do not deploy. */
public class AdminUserDao {

    /**
     * SEEDED FLAW (SQL injection #3): the search term is concatenated into the SQL string.
     * The password column is never selected.
     */
    public Page<AppUser> search(String term, int page) throws SQLException {
        String sql = "SELECT u.id, u.username, u.display_name, u.active, r.name AS role_name, count(*) OVER() AS total_rows "
                + "FROM app_users u JOIN roles r ON r.id = u.role_id "
                + "WHERE lower(u.username) LIKE lower('%" + term + "%') "
                + "OR lower(u.display_name) LIKE lower('%" + term + "%') "
                + "ORDER BY u.username, u.id LIMIT " + Paging.PAGE_SIZE + " OFFSET " + Paging.offset(page);
        List<AppUser> rows = new ArrayList<AppUser>();
        int total = 0;
        try (Connection con = Db.get(); Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(map(rs));
                total = rs.getInt("total_rows");
            }
        }
        return new Page<AppUser>(rows, page, total);
    }

    public AppUser findById(int id) throws SQLException {
        String sql = "SELECT u.id, u.username, u.display_name, u.active, r.name AS role_name "
                + "FROM app_users u JOIN roles r ON r.id = u.role_id WHERE u.id = ?";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public boolean usernameTaken(String username) throws SQLException {
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(
                "SELECT count(*) FROM app_users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    public List<String> roleNames() throws SQLException {
        List<String> out = new ArrayList<String>();
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement("SELECT name FROM roles ORDER BY id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    public int insert(String username, String password, String displayName, String role, boolean active)
            throws SQLException {
        String sql = "INSERT INTO app_users (id, username, password, display_name, role_id, active) "
                + "VALUES ((SELECT COALESCE(max(id), 0) + 1 FROM app_users), ?, ?, ?, (SELECT id FROM roles WHERE name = ?), ?) "
                + "RETURNING id";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, displayName);
            ps.setString(4, role);
            ps.setBoolean(5, active);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** A blank password keeps the stored one. */
    public void update(int id, String password, String displayName, String role, boolean active) throws SQLException {
        String sql = "UPDATE app_users SET display_name = ?, role_id = (SELECT id FROM roles WHERE name = ?), active = ?, "
                + "password = CASE WHEN ? = '' THEN password ELSE ? END WHERE id = ?";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, displayName);
            ps.setString(2, role);
            ps.setBoolean(3, active);
            ps.setString(4, password);
            ps.setString(5, password);
            ps.setInt(6, id);
            ps.executeUpdate();
        }
    }

    public int countActiveAdmins() throws SQLException {
        String sql = "SELECT count(*) FROM app_users u JOIN roles r ON r.id = u.role_id "
                + "WHERE r.name = 'ADMIN' AND u.active";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public void setActive(int id, boolean active) throws SQLException {
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(
                "UPDATE app_users SET active = ? WHERE id = ?")) {
            ps.setBoolean(1, active);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private AppUser map(ResultSet rs) throws SQLException {
        AppUser u = new AppUser();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setDisplayName(rs.getString("display_name"));
        u.setActive(rs.getBoolean("active"));
        u.setRole(rs.getString("role_name"));
        return u;
    }
}
