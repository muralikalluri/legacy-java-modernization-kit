package com.harborclaims.modern.common;

public final class Paging {

    public static final int PAGE_SIZE = 10;

    private Paging() {
    }

    /** Missing, non-numeric or below 1 means page 1. Surrounding spaces and a leading plus are accepted, like Integer.parseInt. */
    public static int parsePage(String raw) {
        if (raw == null) {
            return 1;
        }
        try {
            int p = Integer.parseInt(raw.trim());
            return p < 1 ? 1 : p;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    public static int offset(int page) {
        return (int) Math.min((long) (page - 1) * PAGE_SIZE, Integer.MAX_VALUE);
    }
}
