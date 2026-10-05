package com.harborclaims.portal.policies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

public class PolicyRulesTest {

    private static final LocalDate END = LocalDate.of(2026, 3, 31);

    @Test
    public void policyIsInForceThroughItsEndDateInclusive() {
        assertFalse(PolicyRules.shouldLapse("ACTIVE", END, END.minusDays(1)));
        assertFalse(PolicyRules.shouldLapse("ACTIVE", END, END));
        assertTrue(PolicyRules.shouldLapse("ACTIVE", END, END.plusDays(1)));
    }

    @Test
    public void onlyActivePoliciesLapse() {
        assertFalse(PolicyRules.shouldLapse("LAPSED", END, END.plusDays(30)));
        assertFalse(PolicyRules.shouldLapse("CANCELLED", END, END.plusDays(30)));
    }

    @Test
    public void renewalExtendsFromTheOldEndDate() {
        assertEquals(LocalDate.of(2027, 3, 31), PolicyRules.renewedEnd(END));
        assertEquals("a leap-day end becomes 28 February", LocalDate.of(2025, 2, 28),
                PolicyRules.renewedEnd(LocalDate.of(2024, 2, 29)));
    }

    @Test
    public void cancelledPoliciesCannotBeRenewed() {
        assertEquals("error.policy.renew.cancelled", PolicyRules.renewalBlocker("CANCELLED", END, END));
    }

    @Test
    public void renewalMustLeaveThePolicyInForce() {
        LocalDate today = LocalDate.of(2026, 10, 5);
        assertEquals("error.policy.renew.still.expired",
                PolicyRules.renewalBlocker("LAPSED", LocalDate.of(2025, 9, 1), today));
        assertNull(PolicyRules.renewalBlocker("LAPSED", LocalDate.of(2025, 10, 5), today));
        assertNull(PolicyRules.renewalBlocker("ACTIVE", END, today));
    }

    @Test
    public void cancelNeedsAnActivePolicyWithNoOpenClaims() {
        assertNull(PolicyRules.cancelBlocker("ACTIVE", 0));
        assertEquals("error.policy.cancel.open.claims", PolicyRules.cancelBlocker("ACTIVE", 1));
        assertEquals("error.policy.cancel.status", PolicyRules.cancelBlocker("LAPSED", 0));
        assertEquals("error.policy.cancel.status", PolicyRules.cancelBlocker("CANCELLED", 0));
    }

    @Test
    public void approvedButUnpaidClaimsAreStillOpen() {
        assertTrue(PolicyRules.isOpenClaimStatus("APPROVED"));
        assertTrue(PolicyRules.isOpenClaimStatus("REOPENED"));
        assertFalse(PolicyRules.isOpenClaimStatus("PAID"));
        assertFalse(PolicyRules.isOpenClaimStatus("DENIED"));
    }

    @Test
    public void raceSafetyRestsOnConditionalUpdates() {
        assertTrue(PolicyDao.LAPSE_SQL.contains("status = 'ACTIVE'") && PolicyDao.LAPSE_SQL.contains("end_date < ?"));
        assertTrue(PolicyDao.CANCEL_SQL.contains("AND status = 'ACTIVE'"));
        assertTrue(PolicyDao.RENEW_SQL.contains("status = ?") && PolicyDao.RENEW_SQL.contains("end_date = ?"));
    }
}
