package com.harborclaims.portal.claims;

import java.math.BigDecimal;
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
public class ClaimDao {

    private static final String SELECT = "SELECT cl.id, cl.claim_number, cl.policy_id, p.policy_number, cl.customer_id, "
            + "c.first_name || ' ' || c.last_name AS customer_name, cl.status, "
            + "to_char(cl.loss_date, 'YYYY-MM-DD') AS loss_date, to_char(cl.reported_date, 'YYYY-MM-DD') AS reported_date, "
            + "cl.amount_claimed, cl.amount_approved, cl.description";
    private static final String FROM = " FROM claims cl JOIN policies p ON p.id = cl.policy_id "
            + "JOIN customers c ON c.id = cl.customer_id";

    /**
     * SEEDED FLAW (SQL injection #2): term and status are concatenated into the SQL string.
     * One statement builds both the rows and the total through a window count.
     */
    public Page<Claim> search(String term, String status, int page) throws SQLException {
        String statusClause = status.length() == 0 ? "" : " AND cl.status = '" + status + "'";
        String sql = SELECT + ", count(*) OVER() AS total_rows" + FROM
                + " WHERE (lower(c.last_name) LIKE lower('%" + term + "%') "
                + "OR lower(cl.claim_number) LIKE lower('%" + term + "%'))" + statusClause
                + " ORDER BY cl.id LIMIT " + Paging.PAGE_SIZE + " OFFSET " + Paging.offset(page);
        List<Claim> rows = new ArrayList<Claim>();
        int total = 0;
        try (Connection con = Db.get(); Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(map(rs));
                total = rs.getInt("total_rows");
            }
        }
        return new Page<Claim>(rows, page, total);
    }

    public Claim findById(int id) throws SQLException {
        String sql = SELECT + FROM + " WHERE cl.id = ?";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<ClaimNote> notes(int claimId) throws SQLException {
        String sql = "SELECT author, note, to_char(created_at, 'YYYY-MM-DD HH24:MI') AS created_at "
                + "FROM claim_notes WHERE claim_id = ? ORDER BY id";
        List<ClaimNote> out = new ArrayList<ClaimNote>();
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, claimId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ClaimNote n = new ClaimNote();
                    n.setAuthor(rs.getString("author"));
                    n.setNote(rs.getString("note"));
                    n.setCreatedAt(rs.getString("created_at"));
                    out.add(n);
                }
            }
        }
        return out;
    }

    /** Inserts a NEW claim and its number in one transaction. Returns the id. */
    public int insert(int policyId, int customerId, java.time.LocalDate lossDate, BigDecimal amount,
            String description) throws SQLException {
        String sql = "INSERT INTO claims (claim_number, policy_id, customer_id, status, loss_date, reported_date, "
                + "amount_claimed, description) VALUES ('PENDING', ?, ?, 'NEW', ?, CURRENT_DATE, ?, ?) RETURNING id";
        try (Connection con = Db.get()) {
            con.setAutoCommit(false);
            try {
                int id;
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, policyId);
                    ps.setInt(2, customerId);
                    ps.setDate(3, Date.valueOf(lossDate));
                    ps.setBigDecimal(4, amount);
                    ps.setString(5, description);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        id = rs.getInt(1);
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE claims SET claim_number = 'CLM-' || lpad(id::text, 6, '0') WHERE id = ?")) {
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

    /** Conditional update. Returns false when the claim was no longer in {@code from}. */
    public boolean transition(int id, String from, String to, BigDecimal approved, String author, String note)
            throws SQLException {
        try (Connection con = Db.get()) {
            con.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE claims SET status = ?, amount_approved = COALESCE(?, amount_approved), "
                                + "updated_at = now() WHERE id = ? AND status = ?")) {
                    ps.setString(1, to);
                    if (approved == null) {
                        ps.setNull(2, Types.NUMERIC);
                    } else {
                        ps.setBigDecimal(2, approved);
                    }
                    ps.setInt(3, id);
                    ps.setString(4, from);
                    rows = ps.executeUpdate();
                }
                if (rows == 0) {
                    con.rollback();
                    return false;
                }
                insertNote(con, id, author, note);
                con.commit();
                return true;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    public void addNote(int claimId, String author, String note) throws SQLException {
        try (Connection con = Db.get()) {
            insertNote(con, claimId, author, note);
        }
    }

    private void insertNote(Connection con, int claimId, String author, String note) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO claim_notes (claim_id, author, note) VALUES (?, ?, ?)")) {
            ps.setInt(1, claimId);
            ps.setString(2, author);
            ps.setString(3, note);
            ps.executeUpdate();
        }
    }

    private Claim map(ResultSet rs) throws SQLException {
        Claim c = new Claim();
        c.setId(rs.getInt("id"));
        c.setClaimNumber(rs.getString("claim_number"));
        c.setPolicyId(rs.getInt("policy_id"));
        c.setPolicyNumber(rs.getString("policy_number"));
        c.setCustomerId(rs.getInt("customer_id"));
        c.setCustomerName(rs.getString("customer_name"));
        c.setStatus(rs.getString("status"));
        c.setLossDate(rs.getString("loss_date"));
        c.setReportedDate(rs.getString("reported_date"));
        c.setAmountClaimed(rs.getBigDecimal("amount_claimed"));
        c.setAmountApproved(rs.getBigDecimal("amount_approved"));
        c.setDescription(rs.getString("description"));
        return c;
    }
}
