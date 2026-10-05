package com.harborclaims.portal.policies;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.harborclaims.portal.common.Db;

public class PolicyDao {

    private static final String COLS = "id, policy_number, customer_id, product_type, coverage_limit, premium, "
            + "to_char(start_date, 'YYYY-MM-DD') AS start_date, to_char(end_date, 'YYYY-MM-DD') AS end_date, status";

    public Policy findByNumber(String policyNumber) throws SQLException {
        String sql = "SELECT " + COLS + " FROM policies WHERE policy_number = ?";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, policyNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public Policy findById(int id) throws SQLException {
        String sql = "SELECT " + COLS + " FROM policies WHERE id = ?";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<Policy> listByCustomer(int customerId) throws SQLException {
        String sql = "SELECT " + COLS + " FROM policies WHERE customer_id = ? ORDER BY id";
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
        return p;
    }
}
