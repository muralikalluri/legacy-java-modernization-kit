package com.harborclaims.portal.policies;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** Pure policy lifecycle rules. See docs/adr/0001-legacy-business-rules.md. */
public final class PolicyRules {

    public static final String ACTIVE = "ACTIVE";
    public static final String LAPSED = "LAPSED";
    public static final String CANCELLED = "CANCELLED";

    public static final List<String> ALL_STATUSES = Arrays.asList(ACTIVE, LAPSED, CANCELLED);
    public static final List<String> PRODUCTS = Arrays.asList("AUTO", "HOME", "LIFE");

    /** Claim statuses that block cancelling the policy: everything that is not final. */
    public static final List<String> OPEN_CLAIM_STATUSES =
            Arrays.asList("NEW", "UNDER_REVIEW", "REOPENED", "APPROVED");

    private PolicyRules() {
    }

    /** A policy is in force through its end date inclusive, so it lapses only when the end date is before asOf. */
    public static boolean shouldLapse(String status, LocalDate endDate, LocalDate asOf) {
        return ACTIVE.equals(status) && endDate.isBefore(asOf);
    }

    /** Renewal extends from the old end date, not from today. A 29 February end becomes 28 February. */
    public static LocalDate renewedEnd(LocalDate oldEnd) {
        return oldEnd.plusYears(1);
    }

    /** Returns null when renewal is allowed, otherwise the message key explaining why not. */
    public static String renewalBlocker(String status, LocalDate oldEnd, LocalDate today) {
        if (CANCELLED.equals(status)) {
            return "error.policy.renew.cancelled";
        }
        if (renewedEnd(oldEnd).isBefore(today)) {
            return "error.policy.renew.still.expired";
        }
        return null;
    }

    /** Returns null when cancelling is allowed, otherwise the message key explaining why not. */
    public static String cancelBlocker(String status, int openClaims) {
        if (!ACTIVE.equals(status)) {
            return "error.policy.cancel.status";
        }
        if (openClaims > 0) {
            return "error.policy.cancel.open.claims";
        }
        return null;
    }

    public static boolean isOpenClaimStatus(String claimStatus) {
        return OPEN_CLAIM_STATUSES.contains(claimStatus);
    }
}
