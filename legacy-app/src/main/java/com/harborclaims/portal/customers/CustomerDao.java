package com.harborclaims.portal.customers;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import com.harborclaims.portal.common.Db;
import com.harborclaims.portal.common.Page;
import com.harborclaims.portal.common.Paging;

/** Deliberately insecure for demonstration. Do not deploy. */
public class CustomerDao {

    private static final String COLS = "id, first_name, last_name, email, phone, "
            + "to_char(date_of_birth, 'YYYY-MM-DD') AS date_of_birth, address_line, city, state, postal_code";

    /**
     * SEEDED FLAW (SQL injection #1): the search term is concatenated into the SQL string.
     * One statement builds both the rows and the total through a window count.
     */
    public Page<Customer> search(String term, int page) throws SQLException {
        String sql = "SELECT " + COLS + ", count(*) OVER() AS total_rows FROM customers "
                + "WHERE lower(first_name) LIKE lower('%" + term + "%') "
                + "OR lower(last_name) LIKE lower('%" + term + "%') "
                + "OR lower(email) LIKE lower('%" + term + "%') "
                + "ORDER BY last_name, first_name, id LIMIT " + Paging.PAGE_SIZE + " OFFSET " + Paging.offset(page);
        List<Customer> rows = new ArrayList<Customer>();
        int total = 0;
        try (Connection con = Db.get(); Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(map(rs));
                total = rs.getInt("total_rows");
            }
        }
        return new Page<Customer>(rows, page, total);
    }

    public Customer findById(int id) throws SQLException {
        String sql = "SELECT " + COLS + " FROM customers WHERE id = ?";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public boolean emailInUse(String email, int excludeId) throws SQLException {
        String sql = "SELECT count(*) FROM customers WHERE lower(email) = lower(?) AND id <> ?";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setInt(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    public int insert(Customer c) throws SQLException {
        String sql = "INSERT INTO customers (first_name, last_name, email, phone, date_of_birth, address_line, "
                + "city, state, postal_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, c);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void update(Customer c) throws SQLException {
        String sql = "UPDATE customers SET first_name = ?, last_name = ?, email = ?, phone = ?, date_of_birth = ?, "
                + "address_line = ?, city = ?, state = ?, postal_code = ? WHERE id = ?";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, c);
            ps.setInt(10, c.getId());
            ps.executeUpdate();
        }
    }

    public boolean delete(int id) throws SQLException {
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement("DELETE FROM customers WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private void bind(PreparedStatement ps, Customer c) throws SQLException {
        ps.setString(1, c.getFirstName());
        ps.setString(2, c.getLastName());
        ps.setString(3, c.getEmail());
        ps.setString(4, emptyToNull(c.getPhone()));
        if (emptyToNull(c.getDateOfBirth()) == null) {
            ps.setNull(5, Types.DATE);
        } else {
            ps.setDate(5, Date.valueOf(c.getDateOfBirth()));
        }
        ps.setString(6, emptyToNull(c.getAddressLine()));
        ps.setString(7, emptyToNull(c.getCity()));
        ps.setString(8, emptyToNull(c.getState()));
        ps.setString(9, emptyToNull(c.getPostalCode()));
    }

    private String emptyToNull(String s) {
        return s == null || s.trim().length() == 0 ? null : s.trim();
    }

    private Customer map(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setId(rs.getInt("id"));
        c.setFirstName(rs.getString("first_name"));
        c.setLastName(rs.getString("last_name"));
        c.setEmail(rs.getString("email"));
        c.setPhone(rs.getString("phone"));
        c.setDateOfBirth(rs.getString("date_of_birth"));
        c.setAddressLine(rs.getString("address_line"));
        c.setCity(rs.getString("city"));
        c.setState(rs.getString("state"));
        c.setPostalCode(rs.getString("postal_code"));
        return c;
    }
}
