package com.harborclaims.portal.common;

public class NotFoundException extends Exception {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String what) {
        super(what);
    }
}
