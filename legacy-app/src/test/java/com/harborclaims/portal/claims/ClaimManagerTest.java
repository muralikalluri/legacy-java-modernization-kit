package com.harborclaims.portal.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.harborclaims.portal.policies.Policy;

public class ClaimManagerTest {

    private static Policy policy(String product, String limit, String start, String status) {
        Policy p = new Policy();
        p.setId(1);
        p.setPolicyNumber("POL-000001");
        p.setProductType(product);
        p.setCoverageLimit(new BigDecimal(limit));
        p.setStartDate(start);
        p.setEndDate("2026-12-31");
        p.setStatus(status);
        return p;
    }

    private static Claim claim(int id, String status, String loss, String reported, String amount) {
        Claim c = new Claim();
        c.setId(id);
        c.setClaimNumber("CLM-00000" + id);
        c.setPolicyId(1);
        c.setCustomerName("Test Person");
        c.setStatus(status);
        c.setLossDate(loss);
        c.setReportedDate(reported);
        c.setAmountClaimed(new BigDecimal(amount));
        return c;
    }

    // ---- fraud flags ----

    @Test
    public void newPolicyFlagIsInclusiveOfThirtyDays() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        assertFalse(ClaimManager.isWithinDaysOfStart(start.minusDays(1), start, 30));
        assertTrue(ClaimManager.isWithinDaysOfStart(start, start, 30));
        assertTrue(ClaimManager.isWithinDaysOfStart(start.plusDays(30), start, 30));
        assertFalse(ClaimManager.isWithinDaysOfStart(start.plusDays(31), start, 30));
    }

    @Test
    public void nearLimitStartsAtNinetyPercent() {
        BigDecimal limit = new BigDecimal("50000.00");
        assertFalse(ClaimManager.isNearLimit(new BigDecimal("44999.99"), limit));
        assertTrue(ClaimManager.isNearLimit(new BigDecimal("45000.00"), limit));
    }

    @Test
    public void roundAmountsOnlyCountFromFiveThousand() {
        assertFalse(ClaimManager.isSuspiciouslyRound(new BigDecimal("4000.00")));
        assertTrue(ClaimManager.isSuspiciouslyRound(new BigDecimal("5000.00")));
        assertFalse(ClaimManager.isSuspiciouslyRound(new BigDecimal("5000.50")));
    }

    @Test
    public void lateReportMeansMoreThanSixtyDays() {
        LocalDate loss = LocalDate.of(2026, 1, 1);
        assertFalse(ClaimManager.isLateReport(loss, loss.plusDays(60), 60));
        assertTrue(ClaimManager.isLateReport(loss, loss.plusDays(61), 60));
    }

    @Test
    public void recentSiblingIgnoresTheClaimItself() {
        Claim a = claim(1, "NEW", "2026-03-01", "2026-03-02", "100.00");
        Claim b = claim(2, "NEW", "2026-05-31", "2026-06-01", "100.00");
        Claim c = claim(3, "NEW", "2026-06-01", "2026-06-02", "100.00");
        LocalDate loss = LocalDate.parse(a.getLossDate());
        assertFalse(ClaimManager.hasRecentSibling(a, loss, Arrays.asList(a), 90));
        assertFalse(ClaimManager.hasRecentSibling(a, loss, Arrays.asList(a, b, c), 90));
        Claim boundary = claim(5, "NEW", "2026-05-30", "2026-05-31", "100.00");
        assertTrue("exactly 90 days apart is still recent", ClaimManager.hasRecentSibling(a, loss, Arrays.asList(boundary), 90));
        Claim near = claim(4, "NEW", "2026-04-15", "2026-04-16", "100.00");
        assertTrue(ClaimManager.hasRecentSibling(a, loss, Arrays.asList(a, near), 90));
    }

    @Test
    public void duplicateNeedsSameDayAndAmountWithinOnePercent() {
        Claim a = claim(1, "NEW", "2026-03-01", "2026-03-02", "1000.00");
        Claim close = claim(2, "NEW", "2026-03-01", "2026-03-02", "995.00");
        Claim far = claim(3, "NEW", "2026-03-01", "2026-03-02", "900.00");
        Claim otherDay = claim(4, "NEW", "2026-03-02", "2026-03-03", "1000.00");
        assertTrue(ClaimManager.looksLikeDuplicate(a, close));
        assertFalse(ClaimManager.looksLikeDuplicate(a, far));
        assertFalse(ClaimManager.looksLikeDuplicate(a, otherDay));
    }

    @Test
    public void fraudFlagsCombineAndAreDescribed() {
        Policy p = policy("AUTO", "10000.00", "2026-01-01", "LAPSED");
        Claim c = claim(1, "NEW", "2026-01-10", "2026-04-01", "9500.00");
        List<String> flags = ClaimManager.fraudFlags(c, p, Collections.singletonList(c), LocalDate.of(2026, 4, 2));
        assertEquals(Arrays.asList("NEW_POLICY", "NEAR_LIMIT", "LATE_REPORT", "POLICY_INACTIVE"), flags);
        assertEquals("Policy is not active", ClaimManager.describeFlag("POLICY_INACTIVE"));
        assertEquals(4, ClaimManager.describeFlags(flags).size());
    }

    @Test
    public void aCleanClaimTripsNothing() {
        Policy p = policy("HOME", "100000.00", "2026-01-01", "ACTIVE");
        Claim c = claim(1, "NEW", "2026-06-10", "2026-06-12", "1234.56");
        assertTrue(ClaimManager.fraudFlags(c, p, Collections.singletonList(c), LocalDate.of(2026, 6, 13)).isEmpty());
    }

    // ---- reserves ----

    @Test
    public void reserveRatesFollowProductAndStatus() {
        assertEquals(new BigDecimal("0.60"), ClaimManager.reserveRate("AUTO", "NEW"));
        assertEquals(new BigDecimal("0.85"), ClaimManager.reserveRate("HOME", "UNDER_REVIEW"));
        assertEquals(new BigDecimal("1.00"), ClaimManager.reserveRate("LIFE", "REOPENED"));
        assertEquals(BigDecimal.ONE, ClaimManager.reserveRate("AUTO", "APPROVED"));
        assertEquals(BigDecimal.ZERO, ClaimManager.reserveRate("AUTO", "PAID"));
        assertEquals(BigDecimal.ZERO, ClaimManager.reserveRate("HOME", "DENIED"));
    }

    @Test
    public void approvedClaimsReserveTheApprovedAmount() {
        Claim c = claim(1, "APPROVED", "2026-03-01", "2026-03-02", "1000.00");
        c.setAmountApproved(new BigDecimal("800.00"));
        assertEquals(new BigDecimal("800.00"), ClaimManager.reserveAmount(c, "AUTO"));
        Claim open = claim(2, "NEW", "2026-03-01", "2026-03-02", "1000.00");
        assertEquals(new BigDecimal("600.00"), ClaimManager.reserveAmount(open, "AUTO"));
        assertEquals(new BigDecimal("1400.00"), ClaimManager.totalReserve(Arrays.asList(c, open), "AUTO"));
    }

    // ---- SLA ----

    @Test
    public void slaStatesFollowTheAllowedDays() {
        Claim c = claim(1, "NEW", "2026-03-01", "2026-03-01", "100.00");
        assertEquals("OK", ClaimManager.slaState(c, LocalDate.of(2026, 3, 1)));
        assertEquals("DUE_SOON", ClaimManager.slaState(c, LocalDate.of(2026, 3, 2)));
        assertEquals("DUE_SOON", ClaimManager.slaState(c, LocalDate.of(2026, 3, 3)));
        assertEquals("OVERDUE", ClaimManager.slaState(c, LocalDate.of(2026, 3, 4)));
        assertEquals("NONE", ClaimManager.slaState(claim(2, "PAID", "2026-03-01", "2026-03-01", "1.00"), LocalDate.of(2026, 9, 1)));
    }

    @Test
    public void daysOpenNeverGoesNegative() {
        Claim c = claim(1, "NEW", "2026-03-01", "2026-03-10", "100.00");
        assertEquals(0, ClaimManager.daysOpen(c, LocalDate.of(2026, 3, 1)));
        assertEquals(5, ClaimManager.daysOpen(c, LocalDate.of(2026, 3, 15)));
    }

    @Test
    public void slaTextExplainsTheState() {
        Claim c = claim(1, "NEW", "2026-03-01", "2026-03-01", "100.00");
        assertTrue(ClaimManager.slaText(c, LocalDate.of(2026, 3, 6)).startsWith("Overdue by 3 day(s)"));
        assertTrue(ClaimManager.slaText(claim(2, "PAID", "2026-03-01", "2026-03-01", "1.00"), LocalDate.of(2026, 4, 1))
                .startsWith("No SLA applies"));
    }

    @Test
    public void hintsDependOnStatusAndRole() {
        assertEquals("Start the review.", ClaimManager.nextActionHint("NEW", "ADJUSTER"));
        assertEquals("Waiting for an adjuster to start the review.", ClaimManager.nextActionHint("NEW", "CSR"));
        assertEquals("Denied. Only a supervisor can reopen it.", ClaimManager.nextActionHint("DENIED", "ADJUSTER"));
        assertEquals("Closed. Nothing more to do.", ClaimManager.nextActionHint("PAID", "ADMIN"));
    }

    // ---- letters ----

    @Test
    public void lettersCarryTheClaimFacts() {
        Policy p = policy("AUTO", "50000.00", "2026-01-01", "ACTIVE");
        Claim c = claim(1, "APPROVED", "2026-03-01", "2026-03-02", "12345.50");
        c.setAmountApproved(new BigDecimal("12000.00"));
        String letter = ClaimManager.letterFor(c, p, "");
        assertTrue(letter, letter.contains("CLM-000001") && letter.contains("12,000.00") && letter.contains("POL-000001"));
        c.setStatus("DENIED");
        assertTrue(ClaimManager.letterFor(c, p, "Not covered").contains("Reason: Not covered"));
        assertTrue(ClaimManager.letterFor(c, p, "").contains("see claim notes"));
        c.setStatus("NEW");
        assertEquals("", ClaimManager.letterFor(c, p, ""));
    }

    @Test
    public void lastDenialReasonComesFromTheNewestDenialNote() {
        List<ClaimNote> notes = new ArrayList<ClaimNote>();
        notes.add(note("Status changed from UNDER_REVIEW to DENIED: first reason"));
        notes.add(note("Status changed from DENIED to REOPENED"));
        notes.add(note("Status changed from UNDER_REVIEW to DENIED: second reason"));
        assertEquals("second reason", ClaimManager.lastDenialReason(notes));
        assertEquals("", ClaimManager.lastDenialReason(Collections.<ClaimNote>emptyList()));
    }

    private static ClaimNote note(String text) {
        ClaimNote n = new ClaimNote();
        n.setNote(text);
        return n;
    }

    // ---- report aggregation ----

    @Test
    public void statusRowsIncludeEveryStatusInWorkflowOrder() {
        List<Object[]> raw = new ArrayList<Object[]>();
        raw.add(new Object[] {"PAID", Integer.valueOf(1), new BigDecimal("300.00")});
        raw.add(new Object[] {"NEW", Integer.valueOf(3), new BigDecimal("100.00")});
        List<ClaimManager.StatusRow> rows = ClaimManager.buildStatusRows(raw);
        assertEquals(6, rows.size());
        assertEquals("NEW", rows.get(0).getStatus());
        assertEquals(3, rows.get(0).getCount());
        assertEquals("75.0%", rows.get(0).getShare());
        assertEquals(0, rows.get(3).getCount());
        assertEquals("0.0%", rows.get(3).getShare());
        assertEquals(4, ClaimManager.grandCount(rows));
        assertEquals(new BigDecimal("400.00"), ClaimManager.grandTotal(rows));
    }

    @Test
    public void emptyStatusReportHasZeroRows() {
        List<ClaimManager.StatusRow> rows = ClaimManager.buildStatusRows(Collections.<Object[]>emptyList());
        assertEquals(6, rows.size());
        assertEquals(0, ClaimManager.grandCount(rows));
        assertEquals("0.0%", rows.get(0).getShare());
    }

    @Test
    public void adjusterRowsAreSortedByNameWithUnassignedLast() {
        List<Object[]> raw = new ArrayList<Object[]>();
        raw.add(new Object[] {null, "Unassigned", Integer.valueOf(10), new BigDecimal("1.00"), Integer.valueOf(4)});
        raw.add(new Object[] {Integer.valueOf(3), "Alba Adjuster", Integer.valueOf(5), new BigDecimal("2.00"), Integer.valueOf(1)});
        raw.add(new Object[] {Integer.valueOf(2), "Aiden Adjuster", Integer.valueOf(6), new BigDecimal("3.00"), Integer.valueOf(7)});
        List<ClaimManager.AdjusterRow> rows = ClaimManager.buildAdjusterRows(raw);
        assertEquals("Aiden Adjuster", rows.get(0).getName());
        assertEquals("Alba Adjuster", rows.get(1).getName());
        assertNull(rows.get(2).getUserId());
        assertEquals("Aiden Adjuster", ClaimManager.busiest(rows).getName());
        assertEquals("Needs an owner", ClaimManager.workloadLabel(rows.get(2)));
        assertEquals("Busy", ClaimManager.workloadLabel(rows.get(0)));
        assertEquals("Light", ClaimManager.workloadLabel(rows.get(1)));
        assertEquals(17, ClaimManager.openPercent(new ClaimManager.AdjusterRow(1, "x", 6, 1, BigDecimal.ONE)));
    }

    // ---- summaries and formatting ----

    @Test
    public void policySummaryCountsOpenAndPaid() {
        Policy p = policy("AUTO", "50000.00", "2026-01-01", "ACTIVE");
        Claim paid = claim(1, "PAID", "2026-03-01", "2026-03-02", "1000.00");
        paid.setAmountApproved(new BigDecimal("900.00"));
        Claim open = claim(2, "NEW", "2026-04-01", "2026-04-02", "2000.00");
        Claim denied = claim(3, "DENIED", "2026-05-01", "2026-05-02", "500.00");
        ClaimManager.PolicyClaimsSummary s = ClaimManager.summarize(Arrays.asList(paid, open, denied), p);
        assertEquals(3, s.getTotal());
        assertEquals(1, s.getOpen());
        assertEquals(1, s.getPaid());
        assertEquals("3,500.00", s.getClaimedDisplay());
        assertEquals("900.00", s.getPaidOutDisplay());
        assertEquals("1,200.00", s.getReserveDisplay());
        assertEquals("3 claim(s); 1 open; 1 paid; claimed 3,500.00", s.getText());
        assertEquals(new BigDecimal("2000.00"), ClaimManager.largestClaim(Arrays.asList(paid, open, denied)));
        assertEquals(Integer.valueOf(1), ClaimManager.countByStatus(Arrays.asList(paid, open, denied)).get("NEW"));
    }

    @Test
    public void legacyMoneyTextMatchesTheSharedFormatter() {
        for (String v : new String[] {"0", "7.5", "999.99", "1000", "12345.67", "1234567.89"}) {
            BigDecimal d = new BigDecimal(v);
            assertEquals(v, com.harborclaims.portal.common.Fmt.money(d), ClaimManager.moneyText(d));
        }
        assertEquals("-", ClaimManager.moneyText(null));
    }

    @Test
    public void textHelpers() {
        assertEquals("ab   ", ClaimManager.padRight("ab", 5));
        assertEquals("   ab", ClaimManager.padLeft("ab", 5));
        assertEquals("05 Mar 2026", ClaimManager.shortDate("2026-03-05"));
        assertEquals("", ClaimManager.shortDate("bad"));
        assertEquals("a; c", ClaimManager.joinNonBlank("; ", "a", "", " ", "c"));
        assertEquals("Under review", ClaimManager.statusLabel("UNDER_REVIEW"));
        assertEquals("badge-paid", ClaimManager.statusCss("PAID"));
        assertEquals("badge-unknown", ClaimManager.statusCss("???"));
    }

    @Test
    public void statusTableHasHeaderRowsAndTotal() {
        List<Object[]> raw = new ArrayList<Object[]>();
        raw.add(new Object[] {"NEW", Integer.valueOf(2), new BigDecimal("1500.50")});
        String table = ClaimManager.statusTable(ClaimManager.buildStatusRows(raw));
        String[] lines = table.split("\n");
        assertEquals(8, lines.length);
        assertTrue(lines[0].startsWith("Status"));
        assertTrue(lines[1].contains("1,500.50") && lines[1].contains("100.0%"));
        assertTrue(lines[7].startsWith("All"));
    }

    @Test
    public void oneLineSummarisesAClaim() {
        Claim c = claim(1, "UNDER_REVIEW", "2026-03-05", "2026-03-06", "1234.50");
        assertEquals("CLM-000001 | Under review | Test Person | 1,234.50 | loss 05 Mar 2026", ClaimManager.oneLine(c));
    }

    // ---- form validation ----

    @Test
    public void formValidationReportsEachProblemWithItsField() {
        List<ClaimManager.FieldProblem> p = ClaimManager.validateClaimForm("", "", "", "");
        assertEquals(4, p.size());
        assertEquals("policyNumber", p.get(0).getField());
        assertEquals("error.required", p.get(0).getKey());
        p = ClaimManager.validateClaimForm("POL-1", "2026-02-30", "12.345", "x");
        assertEquals(2, p.size());
        assertEquals("error.date.invalid", p.get(0).getKey());
        assertEquals("error.amount.invalid", p.get(1).getKey());
        assertTrue(ClaimManager.validateClaimForm("POL-1", "2026-02-27", "12.34", "x").isEmpty());
    }

    @Test
    public void claimManagerIsTheGodClass() throws Exception {
        java.io.File f = new java.io.File("src/main/java/com/harborclaims/portal/claims/ClaimManager.java");
        int lines = java.nio.file.Files.readAllLines(f.toPath()).size();
        assertTrue("ClaimManager has " + lines + " lines", lines >= 1000);
    }
}
