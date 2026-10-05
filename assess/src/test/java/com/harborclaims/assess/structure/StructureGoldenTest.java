package com.harborclaims.assess.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.harborclaims.assess.common.Hashing;
import com.harborclaims.assess.common.Json;
import com.harborclaims.assess.common.Modules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Needs the compiled legacy classes. The CI job builds them first; locally run ./assess/run-all.sh once. */
class StructureGoldenTest {

    private static final Path CLASSES = Path.of("..", "legacy-app", "target", "classes");
    private static final Path LEGACY = Path.of("..", "legacy-app");

    private Map<String, Object> build() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(CLASSES), "legacy-app is not compiled; build it first");
        Set<String> edges = JdepsRunner.run(CLASSES);
        Set<String> imports = SourceImports.packageEdges(LEGACY.resolve("src/main/java"));
        JsonNode inventory = Json.read(Path.of("results", "inventory.json"));
        return StructureBuilder.build(inventory, edges, imports, Modules.load(Path.of("modules.yaml")),
                Hashing.sha256(Path.of("modules.yaml")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void theRealCyclesAreFoundWithWitnesses() throws Exception {
        List<StructureBuilder.Cycle> cycles = (List<StructureBuilder.Cycle>) build().get("packageCycles");
        assertEquals(2, cycles.size());
        assertEquals(List.of("com.harborclaims.portal.auth", "com.harborclaims.portal.common"), cycles.get(0).members());
        assertEquals(List.of("com.harborclaims.portal.claims", "com.harborclaims.portal.customers", "com.harborclaims.portal.policies"),
                cycles.get(1).members(), "Customers is in the same component as Claims and Policies");
        assertTrue(cycles.get(1).witnesses().contains(
                "com.harborclaims.portal.claims.ClaimManager -> com.harborclaims.portal.policies.PolicyManager"));
        assertTrue(cycles.get(1).witnesses().contains(
                "com.harborclaims.portal.policies.PolicyManager -> com.harborclaims.portal.claims.ClaimManager"));
        assertTrue(cycles.get(1).witnesses().contains(
                "com.harborclaims.portal.customers.CustomerManager -> com.harborclaims.portal.policies.PolicyDao"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void theGodClassLineCountMatchesTheInventoryCounter() throws Exception {
        Map<String, Object> s = build();
        List<StructureBuilder.ClassItem> top = (List<StructureBuilder.ClassItem>) s.get("largestClasses");
        assertEquals("com.harborclaims.portal.claims.ClaimManager", top.get(0).className());
        assertTrue(top.get(0).godClass());
        int inventoryLines = 0;
        for (JsonNode j : Json.read(Path.of("results", "inventory.json")).get("javaMain")) {
            if (j.get("className").asText().equals("ClaimManager")) {
                inventoryLines = j.get("lines").asInt();
            }
        }
        assertEquals(inventoryLines, top.get(0).lines(), "same physical line counter as the inventory");
        assertTrue(top.get(0).lines() >= StructureBuilder.GOD_CLASS_LINES);
        assertEquals(1L, ((Map<String, Object>) s.get("summary")).get("godClasses"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void theImportGraphNeverHasEdgesTheBytecodeLacks() throws Exception {
        Map<String, Object> cross = (Map<String, Object>) build().get("crossCheck");
        assertEquals(Set.of(), cross.get("packageEdgesOnlyInImports"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void everyPackageHasAModuleAndPlatformIsDependedOnByAll() throws Exception {
        List<StructureBuilder.ModuleItem> modules = (List<StructureBuilder.ModuleItem>) build().get("modules");
        StructureBuilder.ModuleItem platform = modules.stream().filter(m -> m.name().equals("Platform")).findFirst().orElseThrow();
        assertEquals(0, platform.fanOut(), "Platform depends on nobody outside itself");
        assertEquals(5, platform.fanIn(), "every business module depends on Platform");
    }
}
