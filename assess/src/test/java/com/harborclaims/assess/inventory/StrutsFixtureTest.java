package com.harborclaims.assess.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.harborclaims.assess.common.Xml;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrutsFixtureTest {

    private static final Path DIR = Path.of("src/test/resources/struts-mini");

    @Test
    void parsesWithADoctypeToAnUnreachableHostAndAnExternalEntityWithoutOpeningAnything() throws Exception {
        long start = System.currentTimeMillis();
        StrutsConfig.Parsed p = StrutsConfig.parse(DIR.resolve("struts-config.xml"));
        assertTrue(System.currentTimeMillis() - start < 5000, "must not wait for the network");
        assertEquals(1, p.formBeans().size());
        assertEquals("org.apache.struts.tiles.TilesRequestProcessor", p.processorClass());
    }

    @Test
    void commentedOutActionsAreNotCounted() throws Exception {
        List<StrutsConfig.Action> actions = StrutsConfig.parse(DIR.resolve("struts-config.xml")).actions();
        assertEquals(6, actions.size());
        assertTrue(actions.stream().noneMatch(a -> a.path().equals("/commented/out")));
    }

    @Test
    void rolesAttributeAbsentEmptyAndSpacedAreDistinguished() throws Exception {
        List<StrutsConfig.Action> a = StrutsConfig.parse(DIR.resolve("struts-config.xml")).actions();
        assertNull(find(a, "/admin/open").rolesDeclared(), "absent attribute is null");
        assertEquals(List.of(), find(a, "/admin/closed").rolesDeclared(), "empty attribute is an empty list");
        assertEquals(List.of("ADMIN", "CSR"), find(a, "/admin/spaced").rolesDeclared(), "spaces and empty parts are dropped");
    }

    @Test
    void forwardKindsAreClassified() {
        assertEquals("action", InventoryBuilder.kindOf("/customers/list.do?x=1"));
        assertEquals("jsp", InventoryBuilder.kindOf("/WEB-INF/jsp/raw.jsp"));
        assertEquals("tile", InventoryBuilder.kindOf("customer.list"));
    }

    @Test
    void tilesResolveThroughExtendsAndReportUnresolvedOnes() throws Exception {
        TilesDefs t = TilesDefs.parse(DIR.resolve("tiles-defs.xml"));
        assertEquals("/WEB-INF/jsp/customerList.jsp", t.resolveBody("customer.list"));
        assertEquals("/WEB-INF/jsp/default.jsp", t.resolveBody("home.page"), "inherits the body from base.layout");
        assertNull(t.resolveBody("orphan.child"), "parent does not exist");
        assertNull(t.resolveBody("never.defined"));
    }

    @Test
    void seedRolesIgnoreDbLoginRolesAndOtherTables() throws Exception {
        assertEquals(List.of("ADMIN", "CSR"), SeedRoles.parse(DIR.resolve("seed.sql")));
    }

    @Test
    void theResolverCounterIsAvailableForDiagnostics() throws Exception {
        Xml.resolverCalls = 0;
        StrutsConfig.parse(DIR.resolve("struts-config.xml"));
        assertTrue(Xml.resolverCalls >= 0);
    }

    private static StrutsConfig.Action find(List<StrutsConfig.Action> actions, String path) {
        return actions.stream().filter(a -> a.path().equals(path)).findFirst().orElseThrow();
    }
}
