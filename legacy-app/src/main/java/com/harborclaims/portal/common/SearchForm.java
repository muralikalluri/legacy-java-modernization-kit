package com.harborclaims.portal.common;

import org.apache.struts.action.ActionForm;

/** Shared search form for customers and claims. All fields are Strings on purpose. */
public class SearchForm extends ActionForm {

    private static final long serialVersionUID = 1L;

    private String q = "";
    private String status = "";
    private String page = "";

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public String cleanQ() {
        return q == null ? "" : q.trim();
    }

    public String cleanStatus() {
        return status == null ? "" : status.trim();
    }
}
