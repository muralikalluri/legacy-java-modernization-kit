package com.harborclaims.portal.common;

import java.util.List;

public class Page<T> {

    private final List<T> items;
    private final int page;
    private final int total;

    public Page(List<T> items, int page, int total) {
        this.items = items;
        this.page = page;
        this.total = total;
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getTotal() {
        return total;
    }

    public int getTotalPages() {
        return (total + Paging.PAGE_SIZE - 1) / Paging.PAGE_SIZE;
    }

    public boolean isHasPrev() {
        return page > 1;
    }

    public boolean isHasNext() {
        return page < getTotalPages();
    }

    public int getPrevPage() {
        return page - 1;
    }

    public int getNextPage() {
        return page + 1;
    }
}
