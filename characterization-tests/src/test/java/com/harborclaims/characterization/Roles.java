package com.harborclaims.characterization;

import java.util.List;

/** The six seeded demo users, one per role. The password of each is "username-demo". */
public final class Roles {

    public static final List<String> ALL = List.of("admin", "adjuster1", "supervisor1", "underwriter1", "csr1", "auditor1");
    /** May edit and save customers. */
    public static final List<String> CUSTOMER_WRITERS = List.of("admin", "supervisor1", "csr1");
    public static final List<String> CUSTOMER_READ_ONLY = List.of("adjuster1", "underwriter1", "auditor1");

    private Roles() {
    }
}
