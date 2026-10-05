package com.harborclaims.portal.reports;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import com.harborclaims.portal.claims.ClaimManager;
import com.harborclaims.portal.common.BusinessException;
import com.harborclaims.portal.policies.Policy;
import com.harborclaims.portal.policies.PolicyManager;
import com.harborclaims.portal.claims.ClaimRules;
import com.lowagie.text.DocumentException;

public final class ReportManager {

    public static final int DEFAULT_WINDOW_DAYS = 60;
    public static final int MAX_WINDOW_DAYS = 365;

    private static final ReportManager INSTANCE = new ReportManager();

    private ReportManager() {
    }

    public static ReportManager getInstance() {
        return INSTANCE;
    }

    public List<ClaimManager.StatusRow> claimsByStatus() throws SQLException {
        return ClaimManager.getInstance().statusReport();
    }

    public byte[] claimsByStatusPdf() throws SQLException, DocumentException {
        return PdfReportBuilder.claimsByStatus(claimsByStatus());
    }

    public List<ClaimManager.AdjusterRow> claimsByAdjuster() throws SQLException {
        return ClaimManager.getInstance().adjusterReport();
    }

    /** Active policies ending in [asOf, asOf + days], inclusive, ordered by end date then id. */
    public List<Policy> policyExpiry(LocalDate asOf, int days) throws SQLException {
        return PolicyManager.getInstance().expiringBetween(asOf, asOf.plusDays(days));
    }

    /** Blank means today (UTC). Anything else must be a valid yyyy-MM-dd date. */
    public LocalDate parseAsOf(String raw) throws BusinessException {
        if (raw == null || raw.trim().length() == 0) {
            return PolicyManager.today();
        }
        LocalDate d = ClaimRules.parseDate(raw);
        if (d == null) {
            throw new BusinessException("error.date.invalid", "asOf");
        }
        return d;
    }

    /** Blank means 60. Must be a whole number from 1 to 365. */
    public int parseDays(String raw) throws BusinessException {
        if (raw == null || raw.trim().length() == 0) {
            return DEFAULT_WINDOW_DAYS;
        }
        if (!raw.trim().matches("[0-9]{1,4}")) {
            throw new BusinessException("error.report.days.invalid", MAX_WINDOW_DAYS);
        }
        int days = Integer.parseInt(raw.trim());
        if (days < 1 || days > MAX_WINDOW_DAYS) {
            throw new BusinessException("error.report.days.invalid", MAX_WINDOW_DAYS);
        }
        return days;
    }
}
