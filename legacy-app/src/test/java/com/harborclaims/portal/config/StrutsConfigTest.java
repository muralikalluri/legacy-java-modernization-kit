package com.harborclaims.portal.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Reads the real struts-config.xml and tiles-defs.xml, so wiring mistakes fail the build, not the demo. */
public class StrutsConfigTest {

    private static final Set<String> ROLES =
            new HashSet<String>(Arrays.asList("ADMIN", "ADJUSTER", "SUPERVISOR", "UNDERWRITER", "CSR", "AUDITOR"));

    private static Document struts;
    private static Document tiles;

    @BeforeClass
    public static void load() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setValidating(false);
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        struts = f.newDocumentBuilder().parse(new File("src/main/webapp/WEB-INF/struts-config.xml"));
        tiles = f.newDocumentBuilder().parse(new File("src/main/webapp/WEB-INF/tiles-defs.xml"));
    }

    private static List<Element> actions() {
        List<Element> out = new ArrayList<Element>();
        NodeList nodes = struts.getElementsByTagName("action");
        for (int i = 0; i < nodes.getLength(); i++) {
            out.add((Element) nodes.item(i));
        }
        return out;
    }

    private static Set<String> tileNames() {
        Set<String> names = new HashSet<String>();
        NodeList nodes = tiles.getElementsByTagName("definition");
        for (int i = 0; i < nodes.getLength(); i++) {
            names.add(((Element) nodes.item(i)).getAttribute("name"));
        }
        return names;
    }

    @Test
    public void thereAreExactlyThirtyFiveActionMappings() {
        assertEquals("every <action> element counts, including ForwardAction", 35, actions().size());
    }

    @Test
    public void everyActionTypeIsALoadableClass() throws Exception {
        for (Element a : actions()) {
            Class.forName(a.getAttribute("type"));
        }
    }

    @Test
    public void everyFormBeanIsALoadableClass() throws Exception {
        NodeList nodes = struts.getElementsByTagName("form-bean");
        for (int i = 0; i < nodes.getLength(); i++) {
            Class.forName(((Element) nodes.item(i)).getAttribute("type"));
        }
    }

    @Test
    public void everyTileForwardAndInputExists() {
        Set<String> names = tileNames();
        for (Element a : actions()) {
            NodeList forwards = a.getElementsByTagName("forward");
            for (int i = 0; i < forwards.getLength(); i++) {
                String path = ((Element) forwards.item(i)).getAttribute("path");
                if (!path.startsWith("/")) {
                    assertTrue(path + " is not a tiles definition", names.contains(path));
                }
            }
            String input = a.getAttribute("input");
            if (input.length() > 0 && !input.startsWith("/")) {
                assertTrue(input + " is not a tiles definition", names.contains(input));
            }
            String parameter = a.getAttribute("parameter");
            if (parameter.length() > 0) {
                assertTrue(parameter + " is not a tiles definition", names.contains(parameter));
            }
        }
    }

    @Test
    public void everyTileBodyJspExists() {
        NodeList puts = tiles.getElementsByTagName("put");
        for (int i = 0; i < puts.getLength(); i++) {
            String value = ((Element) puts.item(i)).getAttribute("value");
            if (value.startsWith("/WEB-INF/jsp/")) {
                assertTrue(value + " is missing", new File("src/main/webapp" + value).isFile());
            }
        }
    }

    @Test
    public void everyRolesAttributeUsesKnownRoles() {
        for (Element a : actions()) {
            String roles = a.getAttribute("roles");
            if (roles.length() == 0) {
                continue;
            }
            for (String r : roles.split(",")) {
                assertTrue(a.getAttribute("path") + " has unknown role " + r, ROLES.contains(r));
            }
        }
    }

    @Test
    public void exactlyTwoAdminActionsLackRolesAndTheyAreTheSeededOnes() {
        List<String> unprotected = new ArrayList<String>();
        for (Element a : actions()) {
            String path = a.getAttribute("path");
            if (path.startsWith("/admin/") && a.getAttribute("roles").length() == 0) {
                unprotected.add(path);
            }
        }
        java.util.Collections.sort(unprotected);
        assertEquals(Arrays.asList("/admin/jobRun", "/admin/userSave"), unprotected);
    }

    @Test
    public void gatewayPrefixesBelongOnlyToTheModulesThatMigrate() {
        // M6 routes /customers/** and /claims/search/** to the modern app. Nothing else may sit under them,
        // and everything that stays on legacy must sit outside them.
        List<String> customers = new ArrayList<String>();
        List<String> claimSearch = new ArrayList<String>();
        for (Element a : actions()) {
            String path = a.getAttribute("path");
            if (path.startsWith("/customers/")) {
                customers.add(path);
            }
            if (path.startsWith("/claims/search")) {
                claimSearch.add(path);
            }
        }
        java.util.Collections.sort(customers);
        assertEquals(Arrays.asList("/customers/delete", "/customers/edit", "/customers/list", "/customers/save",
                "/customers/search", "/customers/view"), customers);
        assertEquals(Arrays.asList("/claims/search"), claimSearch);
        for (Element a : actions()) {
            String path = a.getAttribute("path");
            if (path.startsWith("/claim/") || path.startsWith("/policy") || path.startsWith("/reports")
                    || path.startsWith("/admin") || path.startsWith("/login") || path.equals("/home")) {
                assertTrue(path + " must stay on legacy", !path.startsWith("/customers/") && !path.startsWith("/claims/search"));
            }
        }
    }

    @Test
    public void everyJspOnDiskIsUsed() {
        File[] jsps = new File("src/main/webapp/WEB-INF/jsp").listFiles();
        Set<String> referenced = new HashSet<String>();
        NodeList puts = tiles.getElementsByTagName("put");
        for (int i = 0; i < puts.getLength(); i++) {
            referenced.add(((Element) puts.item(i)).getAttribute("value"));
        }
        referenced.add("/WEB-INF/jsp/layout.jsp");
        referenced.add("/WEB-INF/jsp/pager.jsp");
        referenced.add("/WEB-INF/jsp/error.jsp");
        NodeList defs = tiles.getElementsByTagName("definition");
        for (int i = 0; i < defs.getLength(); i++) {
            referenced.add(((Element) defs.item(i)).getAttribute("path"));
        }
        for (File f : jsps) {
            assertTrue(f.getName() + " is not referenced", referenced.contains("/WEB-INF/jsp/" + f.getName()));
        }
    }
}
