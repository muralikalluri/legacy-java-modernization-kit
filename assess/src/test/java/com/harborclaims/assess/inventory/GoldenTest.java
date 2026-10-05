package com.harborclaims.assess.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.harborclaims.assess.common.Json;
import com.harborclaims.assess.common.Modules;
import com.harborclaims.assess.jakarta.JakartaBuilder;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

/** Runs against the real legacy-app in this repository. The expected numbers are facts of that app. */
class GoldenTest {

    private static final Path LEGACY = Path.of("..", "legacy-app");

    private Map<String, Object> inventory() throws Exception {
        return InventoryBuilder.build(LEGACY, Modules.load(Path.of("modules.yaml")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void theRealLegacyAppHasTheDocumentedShape() throws Exception {
        Map<String, Object> inv = inventory();
        Map<String, Object> s = (Map<String, Object>) inv.get("summary");
        assertEquals(35, s.get("actions"));
        assertEquals(2L, s.get("forwardActions"));
        assertEquals(25, s.get("jsps"));
        assertEquals(8, s.get("formBeans"));
        assertEquals(6, s.get("roles"));
        assertEquals(2, s.get("filters"));
        assertEquals(List.of("encoding", "login"),
                ((List<WebXml.Filter>) inv.get("filtersInOrder")).stream().map(WebXml.Filter::name).toList(),
                "filter order is meaningful");
        assertEquals(List.of("ADMIN", "ADJUSTER", "SUPERVISOR", "UNDERWRITER", "CSR", "AUDITOR"), inv.get("roles"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void exactlyTwoAdminActionsHaveNoRolesAttribute() throws Exception {
        List<InventoryBuilder.ActionItem> actions = (List<InventoryBuilder.ActionItem>) inventory().get("actions");
        List<String> open = actions.stream().filter(a -> a.path().startsWith("/admin/") && a.rolesDeclared() == null)
                .map(InventoryBuilder.ActionItem::path).sorted().toList();
        assertEquals(List.of("/admin/jobRun", "/admin/userSave"), open);
    }

    @SuppressWarnings("unchecked")
    @Test
    void actionsSplitAcrossModulesAsDocumented() throws Exception {
        List<InventoryBuilder.ModuleItem> mods = (List<InventoryBuilder.ModuleItem>) inventory().get("modules");
        assertEquals(List.of("Customers:6", "Claims:6", "Policies:7", "Reports:5", "Admin:7", "Platform:4"),
                mods.stream().map(m -> m.name() + ":" + m.actions()).toList());
        assertEquals(35, mods.stream().mapToInt(InventoryBuilder.ModuleItem::actions).sum());
    }

    @SuppressWarnings("unchecked")
    @Test
    void everyActionHasAModuleAndFindingsAreEmpty() throws Exception {
        Map<String, Object> inv = inventory();
        for (InventoryBuilder.ActionItem a : (List<InventoryBuilder.ActionItem>) inv.get("actions")) {
            assertTrue(List.of("Customers", "Claims", "Policies", "Reports", "Admin", "Platform").contains(a.module()), a.path());
        }
        Map<String, Object> findings = (Map<String, Object>) inv.get("findings");
        assertEquals(List.of(), findings.get("unresolvedTiles"));
        assertEquals(List.of(), findings.get("missingActionClasses"));
        assertEquals(List.of(), findings.get("unknownRoles"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void migrationScopeIsCustomersPlusClaimSearch() throws Exception {
        List<InventoryBuilder.ActionItem> actions = (List<InventoryBuilder.ActionItem>) inventory().get("actions");
        List<String> scoped = actions.stream().filter(InventoryBuilder.ActionItem::migrationScope)
                .map(InventoryBuilder.ActionItem::path).sorted().toList();
        assertEquals(7, scoped.size());
        assertTrue(scoped.contains("/claims/search"));
        assertTrue(scoped.stream().filter(p -> p.startsWith("/customers/")).count() == 6);
    }

    @Test
    void outputIsByteIdenticalAcrossRunsLocalesAndTimeZones() throws Exception {
        String first = Json.toString(inventory());
        Locale oldLocale = Locale.getDefault();
        TimeZone oldZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            assertEquals(first, Json.toString(inventory()));
        } finally {
            Locale.setDefault(oldLocale);
            TimeZone.setDefault(oldZone);
        }
        assertFalse(first.contains(LEGACY.toAbsolutePath().normalize().toString()), "no absolute paths");
        assertTrue(first.endsWith("}\n"));
        assertFalse(first.contains("\r"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void jakartaCountsSplitJakartaMigratingFromJdkOwned() throws Exception {
        Map<String, Object> block = Map.of("changedFiles", 0);
        Map<String, Object> out = JakartaBuilder.build(LEGACY, block);
        Map<String, Object> main = (Map<String, Object>) out.get("javaMain");
        assertEquals((int) main.get("javaxImports"),
                (int) main.get("jakartaMigratingImports") + (int) main.get("jdkOwnedImports") + (int) main.get("otherImports"));
        assertEquals(90, main.get("jakartaMigratingImports"), "javax.servlet (14) + javax.servlet.http (76)");
        assertEquals(4, main.get("jdkOwnedImports"), "javax.naming (3) + javax.sql (1) stay javax");
        assertEquals(Json.toString(out), Json.toString(JakartaBuilder.build(LEGACY, block)));
    }
}
