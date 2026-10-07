package com.harborclaims.characterization;

/** Which system the suite runs against. The same tests run against legacy now and against the migrated routes later. */
public final class Target {

    public enum Kind {
        LEGACY, MODERN
    }

    private Target() {
    }

    public static String baseUrl() {
        String url = System.getProperty("base.url", "http://localhost:8501");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static Kind kind() {
        return "modern".equalsIgnoreCase(System.getProperty("target", "legacy")) ? Kind.MODERN : Kind.LEGACY;
    }

    /** For log lines only. Tests must not branch on the target (see ContractGuardTest). */
    public static String describe() {
        return kind() + " at " + baseUrl();
    }

    public static boolean isLegacy() {
        return kind() == Kind.LEGACY;
    }
}
