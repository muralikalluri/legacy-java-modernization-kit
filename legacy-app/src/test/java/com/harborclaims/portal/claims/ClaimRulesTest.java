package com.harborclaims.portal.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class ClaimRulesTest {

    private static final BigDecimal AT_THRESHOLD = new BigDecimal("10000.00");
    private static final BigDecimal ABOVE = new BigDecimal("10000.01");
    private static final BigDecimal BELOW = new BigDecimal("9999.99");

    @Test
    public void onlyDocumentedEdgesAreValid() {
        int valid = 0;
        for (String from : ClaimRules.ALL_STATUSES) {
            for (String to : ClaimRules.ALL_STATUSES) {
                if (ClaimRules.isValidEdge(from, to)) {
                    valid++;
                }
            }
        }
        assertEquals(6, valid);
        assertTrue(ClaimRules.isValidEdge("NEW", "UNDER_REVIEW"));
        assertTrue(ClaimRules.isValidEdge("DENIED", "REOPENED"));
        assertTrue(ClaimRules.isValidEdge("REOPENED", "UNDER_REVIEW"));
    }

    @Test
    public void sameStateAndTerminalStatesHaveNoEdges() {
        for (String s : ClaimRules.ALL_STATUSES) {
            assertFalse(s + " to itself", ClaimRules.isValidEdge(s, s));
        }
        for (String to : ClaimRules.ALL_STATUSES) {
            assertFalse("PAID is terminal", ClaimRules.isValidEdge("PAID", to));
        }
        assertFalse("APPROVED cannot be reopened", ClaimRules.isValidEdge("APPROVED", "REOPENED"));
        assertFalse("no skipping review", ClaimRules.isValidEdge("NEW", "APPROVED"));
    }

    @Test
    public void adjusterApprovalThresholdIsInclusive() {
        assertTrue(ClaimRules.mayTransition("ADJUSTER", "UNDER_REVIEW", "APPROVED", BELOW));
        assertTrue(ClaimRules.mayTransition("ADJUSTER", "UNDER_REVIEW", "APPROVED", AT_THRESHOLD));
        assertFalse(ClaimRules.mayTransition("ADJUSTER", "UNDER_REVIEW", "APPROVED", ABOVE));
        assertTrue(ClaimRules.mayTransition("SUPERVISOR", "UNDER_REVIEW", "APPROVED", ABOVE));
        assertTrue(ClaimRules.mayTransition("ADMIN", "UNDER_REVIEW", "APPROVED", ABOVE));
    }

    @Test
    public void thresholdComparesValueNotScale() {
        assertTrue(ClaimRules.mayTransition("ADJUSTER", "UNDER_REVIEW", "APPROVED", new BigDecimal("10000")));
    }

    @Test
    public void onlySupervisorLevelCanPayOrReopen() {
        assertFalse(ClaimRules.mayTransition("ADJUSTER", "APPROVED", "PAID", BELOW));
        assertTrue(ClaimRules.mayTransition("SUPERVISOR", "APPROVED", "PAID", BELOW));
        assertFalse(ClaimRules.mayTransition("ADJUSTER", "DENIED", "REOPENED", BELOW));
        assertTrue(ClaimRules.mayTransition("ADMIN", "DENIED", "REOPENED", BELOW));
    }

    @Test
    public void auditorAndCsrCannotMoveClaims() {
        for (String role : Arrays.asList("AUDITOR", "CSR", "UNDERWRITER")) {
            for (String from : ClaimRules.ALL_STATUSES) {
                assertEquals(role + " from " + from, Collections.emptyList(),
                        ClaimRules.targetsFor(role, from, BELOW));
            }
        }
    }

    @Test
    public void targetsAreOfferedInFixedOrder() {
        assertEquals(Arrays.asList("APPROVED", "DENIED"), ClaimRules.targetsFor("SUPERVISOR", "UNDER_REVIEW", ABOVE));
        assertEquals(Collections.singletonList("DENIED"), ClaimRules.targetsFor("ADJUSTER", "UNDER_REVIEW", ABOVE));
    }

    @Test
    public void moneyParsing() {
        assertEquals(new BigDecimal("12.50"), ClaimRules.parseMoney(" 12.5 "));
        assertEquals(new BigDecimal("0.00"), ClaimRules.parseMoney("0"));
        assertNull(ClaimRules.parseMoney(""));
        assertNull(ClaimRules.parseMoney(null));
        assertNull(ClaimRules.parseMoney("12.345"));
        assertNull(ClaimRules.parseMoney("-5"));
        assertNull(ClaimRules.parseMoney("1e3"));
        assertNull(ClaimRules.parseMoney("1,000"));
    }

    @Test
    public void dateParsingIsStrict() {
        assertEquals(LocalDate.of(2026, 2, 28), ClaimRules.parseDate("2026-02-28"));
        assertNull(ClaimRules.parseDate("2026-02-30"));
        assertNull(ClaimRules.parseDate("2026-2-5"));
        assertNull(ClaimRules.parseDate("28/02/2026"));
        assertNull(ClaimRules.parseDate(null));
    }

    @Test
    public void policyPeriodIsInclusiveOnBothEnds() {
        LocalDate start = LocalDate.of(2025, 3, 1);
        LocalDate end = LocalDate.of(2026, 2, 28);
        assertFalse(ClaimRules.withinPeriod(start.minusDays(1), start, end));
        assertTrue(ClaimRules.withinPeriod(start, start, end));
        assertTrue(ClaimRules.withinPeriod(end, start, end));
        assertFalse(ClaimRules.withinPeriod(end.plusDays(1), start, end));
    }
}
