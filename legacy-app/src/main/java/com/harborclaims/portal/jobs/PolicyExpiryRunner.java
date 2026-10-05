package com.harborclaims.portal.jobs;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

import com.harborclaims.portal.claims.ClaimManager;
import com.harborclaims.portal.common.Db;
import com.harborclaims.portal.policies.PolicyDao;

/**
 * The one place the policy expiry job runs, for both the nightly trigger and the admin "run now".
 * A JVM lock keeps two runs from overlapping (one Tomcat). The work is one idempotent statement:
 * a second run for the same date updates zero rows.
 */
public final class PolicyExpiryRunner {

    public static final String JOB_NAME = "PolicyExpiryJob";
    public static final String NIGHTLY = "NIGHTLY";
    public static final String MANUAL = "MANUAL";

    private static final Logger LOG = Logger.getLogger(PolicyExpiryRunner.class);
    private static final ReentrantLock LOCK = new ReentrantLock();

    private PolicyExpiryRunner() {
    }

    /** Returns the finished run, or null when another run is already in progress. */
    public static JobRun run(final LocalDate asOf, final String triggerType, final String triggeredBy)
            throws SQLException {
        try {
            return guarded(new Callable<JobRun>() {
                public JobRun call() throws Exception {
                    return runLocked(asOf, triggerType, triggeredBy);
                }
            });
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Job failed: " + e.getMessage());
        }
    }

    /** Runs the body only if no run is active (also refusing a nested call from the same thread). */
    static <T> T guarded(Callable<T> body) throws Exception {
        if (LOCK.isHeldByCurrentThread() || !LOCK.tryLock()) {
            return null;
        }
        try {
            return body.call();
        } finally {
            LOCK.unlock();
        }
    }

    private static JobRun runLocked(LocalDate asOf, String triggerType, String triggeredBy) throws SQLException {
        JobRunDao runs = new JobRunDao();
        PolicyDao policies = new PolicyDao();
        int runId = runs.insertRunning(JOB_NAME, triggerType, triggeredBy, asOf);
        try (Connection con = Db.get()) {
            con.setAutoCommit(false);
            try {
                int rows = policies.lapseExpired(con, asOf);
                runs.markSucceeded(con, runId, rows);
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            LOG.error("Policy expiry run " + runId + " failed", e);
            runs.markFailed(runId, e.getMessage());
            return runs.find(runId);
        }
        JobRun done = runs.find(runId);
        LOG.info("Policy expiry run " + runId + " (" + triggerType + ") as of " + asOf + " lapsed " + done.getRowsAffected());
        try {
            LOG.info("Claims by status after the run:\n" + ClaimManager.statusTable(ClaimManager.getInstance().statusReport()));
        } catch (SQLException e) {
            LOG.warn("Could not log the status digest: " + e.getMessage());
        }
        return done;
    }
}
