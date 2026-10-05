package com.harborclaims.portal.jobs;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.harborclaims.portal.common.Db;

public class JobRunDao {

    private static final String COLS = "id, job_name, trigger_type, triggered_by, to_char(as_of, 'YYYY-MM-DD') AS as_of, "
            + "to_char(started_at, 'YYYY-MM-DD HH24:MI:SS') AS started_at, "
            + "to_char(finished_at, 'YYYY-MM-DD HH24:MI:SS') AS finished_at, rows_affected, status, error";

    /** Own transaction: the RUNNING row stays visible even if the work later fails. */
    public int insertRunning(String jobName, String triggerType, String triggeredBy, LocalDate asOf) throws SQLException {
        String sql = "INSERT INTO job_runs (job_name, trigger_type, triggered_by, as_of, status) "
                + "VALUES (?, ?, ?, ?, 'RUNNING') RETURNING id";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, jobName);
            ps.setString(2, triggerType);
            ps.setString(3, triggeredBy);
            ps.setDate(4, Date.valueOf(asOf));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Runs on the caller's connection so the row count commits together with the data change. */
    public void markSucceeded(Connection con, int id, int rows) throws SQLException {
        String sql = "UPDATE job_runs SET status = 'SUCCEEDED', rows_affected = ?, finished_at = now() WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, rows);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void markFailed(int id, String message) throws SQLException {
        String sql = "UPDATE job_runs SET status = 'FAILED', error = ?, finished_at = now() WHERE id = ?";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            String clean = message == null ? "unknown error" : message;
            ps.setString(1, clean.length() > 500 ? clean.substring(0, 500) : clean);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public JobRun find(int id) throws SQLException {
        String sql = "SELECT " + COLS + " FROM job_runs WHERE id = ?";
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<JobRun> recent(int limit) throws SQLException {
        String sql = "SELECT " + COLS + " FROM job_runs ORDER BY id DESC LIMIT ?";
        List<JobRun> out = new ArrayList<JobRun>();
        try (Connection con = Db.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    private JobRun map(ResultSet rs) throws SQLException {
        JobRun r = new JobRun();
        r.setId(rs.getInt("id"));
        r.setJobName(rs.getString("job_name"));
        r.setTriggerType(rs.getString("trigger_type"));
        r.setTriggeredBy(rs.getString("triggered_by"));
        r.setAsOf(rs.getString("as_of"));
        r.setStartedAt(rs.getString("started_at"));
        r.setFinishedAt(rs.getString("finished_at"));
        int rows = rs.getInt("rows_affected");
        r.setRowsAffected(rs.wasNull() ? null : Integer.valueOf(rows));
        r.setStatus(rs.getString("status"));
        r.setError(rs.getString("error"));
        return r;
    }
}
