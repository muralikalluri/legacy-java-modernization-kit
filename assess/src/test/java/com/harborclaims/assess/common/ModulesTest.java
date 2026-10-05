package com.harborclaims.assess.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModulesTest {

    private final Modules modules = load();

    private static Modules load() {
        try {
            return Modules.load(Path.of("modules.yaml"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void actionPathsMapToModules() {
        assertEquals("Customers", modules.forActionPath("/customers/list"));
        assertEquals("Claims", modules.forActionPath("/claim/view"));
        assertEquals("Claims", modules.forActionPath("/claims/search"));
        assertEquals("Policies", modules.forActionPath("/policy/renew"));
        assertEquals("Policies", modules.forActionPath("/policies/list"));
        assertEquals("Reports", modules.forActionPath("/reports/claimsByStatusPdf"));
        assertEquals("Admin", modules.forActionPath("/admin/jobRun"));
        assertEquals("Platform", modules.forActionPath("/login"));
        assertEquals("Platform", modules.forActionPath("/loginSubmit"));
        assertEquals("Platform", modules.forActionPath("/logout"));
        assertEquals("Platform", modules.forActionPath("/home"));
    }

    @Test
    void anUnmappedPathIsAnErrorNotOther() {
        assertThrows(IllegalStateException.class, () -> modules.forActionPath("/mystery/thing"));
        assertThrows(IllegalStateException.class, () -> modules.forPackage("com.example.unknown"));
        assertThrows(IllegalStateException.class, () -> modules.forJsp("strange.jsp"));
    }

    @Test
    void packagesIncludingTheJobsOwner() {
        assertEquals("Policies", modules.forPackage("com.harborclaims.portal.jobs"));
        assertEquals("Claims", modules.forPackage("com.harborclaims.portal.claims"));
        assertEquals("Platform", modules.forPackage("com.harborclaims.portal.common"));
    }

    @Test
    void jspsMapByNameWithSharedOnesInPlatform() {
        assertEquals("Customers", modules.forJsp("customerSearch.jsp"));
        assertEquals("Claims", modules.forJsp("claimView.jsp"));
        assertEquals("Reports", modules.forJsp("reportClaimsByAdjuster.jsp"));
        for (String shared : List.of("layout.jsp", "header.jsp", "footer.jsp", "pager.jsp", "error.jsp", "login.jsp", "home.jsp", "index.jsp")) {
            assertEquals("Platform", modules.forJsp(shared));
        }
    }

    @Test
    void migrationScopeIsCustomersPlusClaimSearch() {
        assertTrue(modules.inMigrationScope("/customers/save"));
        assertTrue(modules.inMigrationScope("/claims/search"));
        assertFalse(modules.inMigrationScope("/claim/view"));
        assertFalse(modules.inMigrationScope("/policy/view"));
    }
}
