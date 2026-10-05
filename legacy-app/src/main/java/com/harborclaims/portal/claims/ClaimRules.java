package com.harborclaims.portal.claims;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.harborclaims.portal.common.Roles;

/** Pure business rules for claims. See docs/adr/0001-legacy-business-rules.md. */
public final class ClaimRules {

    public static final String NEW = "NEW";
    public static final String UNDER_REVIEW = "UNDER_REVIEW";
    public static final String APPROVED = "APPROVED";
    public static final String DENIED = "DENIED";
    public static final String PAID = "PAID";
    public static final String REOPENED = "REOPENED";

    /** Claims strictly above this need a supervisor to approve. */
    public static final BigDecimal SUPERVISOR_THRESHOLD = new BigDecimal("10000.00");

    public static final List<String> ALL_STATUSES =
            Arrays.asList(NEW, UNDER_REVIEW, APPROVED, DENIED, PAID, REOPENED);

    /** Order in which targets are offered to the user. */
    private static final List<String> TARGET_ORDER =
            Arrays.asList(UNDER_REVIEW, APPROVED, DENIED, PAID, REOPENED);

    private ClaimRules() {
    }

    /** Roles allowed on an edge (adjusters on APPROVED are further limited by the threshold), or null if no such edge. */
    static List<String> rolesFor(String from, String to) {
        if (NEW.equals(from) && UNDER_REVIEW.equals(to)) {
            return Arrays.asList(Roles.ADMIN, Roles.ADJUSTER, Roles.SUPERVISOR);
        }
        if (UNDER_REVIEW.equals(from) && APPROVED.equals(to)) {
            return Arrays.asList(Roles.ADMIN, Roles.SUPERVISOR, Roles.ADJUSTER);
        }
        if (UNDER_REVIEW.equals(from) && DENIED.equals(to)) {
            return Arrays.asList(Roles.ADMIN, Roles.ADJUSTER, Roles.SUPERVISOR);
        }
        if (APPROVED.equals(from) && PAID.equals(to)) {
            return Arrays.asList(Roles.ADMIN, Roles.SUPERVISOR);
        }
        if (DENIED.equals(from) && REOPENED.equals(to)) {
            return Arrays.asList(Roles.ADMIN, Roles.SUPERVISOR);
        }
        if (REOPENED.equals(from) && UNDER_REVIEW.equals(to)) {
            return Arrays.asList(Roles.ADMIN, Roles.ADJUSTER, Roles.SUPERVISOR);
        }
        return null;
    }

    public static boolean isValidEdge(String from, String to) {
        return rolesFor(from, to) != null;
    }

    public static boolean mayTransition(String role, String from, String to, BigDecimal claimed) {
        List<String> roles = rolesFor(from, to);
        if (roles == null || !roles.contains(role)) {
            return false;
        }
        if (APPROVED.equals(to) && Roles.ADJUSTER.equals(role)) {
            return claimed.compareTo(SUPERVISOR_THRESHOLD) <= 0;
        }
        return true;
    }

    public static List<String> targetsFor(String role, String from, BigDecimal claimed) {
        List<String> out = new ArrayList<String>();
        for (String to : TARGET_ORDER) {
            if (mayTransition(role, from, to, claimed)) {
                out.add(to);
            }
        }
        return out;
    }

    /** Positive or zero amount with at most 2 decimals, or null when the text is not such a number. */
    public static BigDecimal parseMoney(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (!s.matches("[0-9]{1,10}(\\.[0-9]{1,2})?")) {
            return null;
        }
        return new BigDecimal(s).setScale(2);
    }

    /** Strict yyyy-MM-dd or null. */
    public static LocalDate parseDate(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (!s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Inclusive on both ends. */
    public static boolean withinPeriod(LocalDate day, LocalDate start, LocalDate end) {
        return !day.isBefore(start) && !day.isAfter(end);
    }
}
