package com.harborclaims.portal;

/** Build marker for the legacy portal. Replaced by real modules from M1. */
public final class BuildInfo {

    public static final String APP_NAME = "Harbor Claims Portal";

    private BuildInfo() {
    }

    public static String javaSpecVersion() {
        return System.getProperty("java.specification.version");
    }
}
