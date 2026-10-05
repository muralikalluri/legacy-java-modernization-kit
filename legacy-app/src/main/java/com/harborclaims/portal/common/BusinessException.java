package com.harborclaims.portal.common;

/** A rule violation the user can fix. Carries a message key from ApplicationResources.properties. */
public class BusinessException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String key;
    private final Object[] args;

    public BusinessException(String key, Object... args) {
        super(key);
        this.key = key;
        this.args = args;
    }

    public String getKey() {
        return key;
    }

    public Object[] getArgs() {
        return args;
    }
}
