package com.harborclaims.portal.policies;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.harborclaims.portal.common.Db;
import com.harborclaims.portal.common.Page;
import com.harborclaims.portal.common.Paging;

public class PolicyDao {

    /** The one statement the nightly job and the admin "run now" both execute. */
    public static final String LAPSE_SQL =
            "UPDATE policies SET status = 'LAPSED' WHERE status = 'ACTIVE' AND end_date < ?";
    public static final String CANCEL_SQL =
            "UPDATE policies SET status = 'CANCELLED', cancelled_on = ? WHERE id = ? AND status = 'ACTIVE'";
    public static final String RENEW_SQL =
            "UPDATE policies SET end_date = ?, status = 'ACTIVE' WHERE id = ? AND status = ? AND end_date = ?";

    private static final String COLS = "p.id, p.policy_number, p.customer_id, p.product_type, p.coverage_limit, p.premium, "
            + "to_char(p.start_date, 'YYYY-MM-DD') AS start_date, to_char(p.end_date, 'YYYY-MM-DD') AS end_date, "
            + "p.status, to_char(p.cancelled_on, 'YYYY-MM-DD') AS cancelled_on, c.first_name || ' ' || c.last_name AS customer_name";
    private static final String FROM = " FROM policies p JOIN customers c ON c.id = p.customer_id";

    public Policy findByNumber(String policyNumber) throws SQLException {
        return one("SELECT " + COLS + FROM + " WHERE p.policy_number = ?", policyNumber);
    }

    public Policy findById(int id) throws SQLException {
        return one("SELECT " + COLS + FROM + " WHERE p.id = ?", Integer.valueOf(id));
    }

    private Policy one(String sql, Object param) throws SQLException {
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<Policy> listByCustomer(int customerId) throws SQLException {
        String sql = "SELECT " + COLS + FROM + " WHERE p.customer_id = ? ORDER BY p.id";
        List<Policy> out = new ArrayList<Policy>();
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public int countByCustomer(int customerId) throws SQLException {
        String sql = "SELECT count(*) FROM policies WHERE customer_id = ?";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Parameterised search: policy number or customer last name, optional exact status. */
    public Page<Policy> search(String term, String status, int page) throws SQLException {
        String sql = "SELECT " + COLS + ", count(*) OVER() AS total_rows" + FROM
                + " WHERE (lower(p.policy_number) LIKE lower(?) OR lower(c.last_name) LIKE lower(?))"
                + " AND (? = '' OR p.status = ?) ORDER BY p.id LIMIT ? OFFSET ?";
        List<Policy> rows = new ArrayList<Policy>();
        int total = 0;
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            String like = "%" + term + "%";
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, status);
            ps.setString(4, status);
            ps.setInt(5, Paging.PAGE_SIZE);
            ps.setInt(6, Paging.offset(page));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(map(rs));
                    total = rs.getInt("total_rows");
                }
            }
        }
        return new Page<Policy>(rows, page, total);
    }

    /** Active policies whose end date falls in [from, to], inclusive. */
    public List<Policy> expiringBetween(LocalDate from, LocalDate to) throws SQLException {
        String sql = "SELECT " + COLS + FROM + " WHERE p.status = 'ACTIVE' AND p.end_date >= ? AND p.end_date <= ? "
                + "ORDER BY p.end_date, p.id";
        List<Policy> out = new ArrayList<Policy>();
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public int insert(int customerId, String product, BigDecimal limit, BigDecimal premium, LocalDate start,
            LocalDate end) throws SQLException {
        String sql = "INSERT INTO policies (policy_number, customer_id, product_type, coverage_limit, premium, "
                + "start_date, end_date, status) VALUES ('PENDING', ?, ?, ?, ?, ?, ?, 'ACTIVE') RETURNING id";
        try (Connection con = Db.get()) {
            con.setAutoCommit(false);
            try {
                int id;
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, customerId);
                    ps.setString(2, product);
                    ps.setBigDecimal(3, limit);
                    ps.setBigDecimal(4, premium);
                    ps.setDate(5, Date.valueOf(start));
                    ps.setDate(6, Date.valueOf(end));
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        id = rs.getInt(1);
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE policies SET policy_number = 'POL-' || lpad(id::text, 6, '0') WHERE id = ?")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }
                con.commit();
                return id;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Only ACTIVE policies are editable. Returns false if the status changed in the meantime. */
    public boolean updateTerms(int id, String product, BigDecimal limit, BigDecimal premium) throws SQLException {
        String sql = "UPDATE policies SET product_type = ?, coverage_limit = ?, premium = ? "
                + "WHERE id = ? AND status = 'ACTIVE'";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, product);
            ps.setBigDecimal(2, limit);
            ps.setBigDecimal(3, premium);
            ps.setInt(4, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean cancel(int id, LocalDate today) throws SQLException {
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(CANCEL_SQL)) {
            ps.setDate(1, Date.valueOf(today));
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean renew(int id, String expectedStatus, LocalDate expectedEnd, LocalDate newEnd) throws SQLException {
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(RENEW_SQL)) {
            ps.setDate(1, Date.valueOf(newEnd));
            ps.setInt(2, id);
            ps.setString(3, expectedStatus);
            ps.setDate(4, Date.valueOf(expectedEnd));
            return ps.executeUpdate() > 0;
        }
    }

    /** Runs on the caller's connection so the job can commit the count together with the data. */
    public int lapseExpired(Connection con, LocalDate asOf) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(LAPSE_SQL)) {
            ps.setDate(1, Date.valueOf(asOf));
            return ps.executeUpdate();
        }
    }

    private Policy map(ResultSet rs) throws SQLException {
        Policy p = new Policy();
        p.setId(rs.getInt("id"));
        p.setPolicyNumber(rs.getString("policy_number"));
        p.setCustomerId(rs.getInt("customer_id"));
        p.setProductType(rs.getString("product_type"));
        p.setCoverageLimit(rs.getBigDecimal("coverage_limit"));
        p.setPremium(rs.getBigDecimal("premium"));
        p.setStartDate(rs.getString("start_date"));
        p.setEndDate(rs.getString("end_date"));
        p.setStatus(rs.getString("status"));
        p.setCancelledOn(rs.getString("cancelled_on"));
        p.setCustomerName(rs.getString("customer_name"));
        return p;
    }
}
