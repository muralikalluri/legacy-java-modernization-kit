package com.harborclaims.modern.common;

import java.util.List;

/** One page of rows. {@code total} comes from a window count, so it is 0 on a page past the end (unspecified, as in legacy). */
public record Page<T>(List<T> items, int page, int total) {

    public int totalPages() {
        return (total + Paging.PAGE_SIZE - 1) / Paging.PAGE_SIZE;
    }

    public boolean hasPrev() {
        return page > 1;
    }

    public boolean hasNext() {
        return page < totalPages();
    }
}
