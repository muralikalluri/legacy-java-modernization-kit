package com.harborclaims.assess.inventory;

import com.harborclaims.assess.common.Hashing;
import com.harborclaims.assess.common.Modules;
import com.harborclaims.assess.common.PomReader;
import com.harborclaims.assess.common.SourceTree;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Builds inventory.json from the legacy app's configuration, JSPs and sources. */
public final class InventoryBuilder {

    public static final String TOOL = "assess 1.0.0";

    public record ForwardItem(String name, String path, String kind, boolean redirect, String resolvedBody) {
    }

    public record ActionItem(String path, String module, String type, String kind, String form, String scope,
            String validate, String input, String inputKind, String parameter, List<String> rolesDeclared,
            List<ForwardItem> forwards, boolean migrationScope) {
    }

    public record JspItem(String file, String module, int lines, int scriptlets, int expressions, int declarations,
            int javaxReferences) {
    }

    public record JavaItem(String file, String pkg, String className, String module, int lines, int javaxImports) {
    }

    public record ModuleItem(String name, int actions, int jsps, int javaMainFiles, int javaMainLines, int daos,
            int managers, int actionClasses, int forms, int javaTestFiles, int scriptletBlocks) {
    }

    public record TileItem(String name, String parent, String layout, String resolvedBody) {
    }

    public record Integration(String name, String evidence) {
    }

    private InventoryBuilder() {
    }

    public static Map<String, Object> build(Path root, Modules modules) throws IOException {
        Path webapp = root.resolve("src/main/webapp");
        StrutsConfig.Parsed struts = StrutsConfig.parse(webapp.resolve("WEB-INF/struts-config.xml"));
        WebXml.Parsed web = WebXml.parse(webapp.resolve("WEB-INF/web.xml"));
        TilesDefs tiles = TilesDefs.parse(webapp.resolve("WEB-INF/tiles-defs.xml"));
        List<String> roles = SeedRoles.parse(root.resolve("db/V2__seed.sql"));
        List<PomReader.Dependency> deps = PomReader.dependencies(root.resolve("pom.xml"));

        // ---- Java sources
        List<Path> mainJava = SourceTree.files(root.resolve("src/main/java"), p -> p.toString().endsWith(".java"));
        List<Path> testJava = SourceTree.files(root.resolve("src/test/java"), p -> p.toString().endsWith(".java"));
        List<JavaItem> javaMain = new ArrayList<>();
        Set<String> knownClasses = new TreeSet<>();
        for (Path p : mainJava) {
            JavaScanner.Result r = JavaScanner.scan(Files.readString(p, StandardCharsets.UTF_8));
            javaMain.add(new JavaItem(SourceTree.rel(root, p), r.pkg(), r.className(), modules.forPackage(r.pkg()),
                    r.lines(), r.javaxImports().size()));
            knownClasses.add(r.pkg() + "." + r.className());
        }
        List<JavaItem> javaTest = new ArrayList<>();
        for (Path p : testJava) {
            JavaScanner.Result r = JavaScanner.scan(Files.readString(p, StandardCharsets.UTF_8));
            javaTest.add(new JavaItem(SourceTree.rel(root, p), r.pkg(), r.className(), modules.forPackage(r.pkg()),
                    r.lines(), r.javaxImports().size()));
        }

        // ---- JSPs: every .jsp under src/main/webapp
        List<Path> jspFiles = SourceTree.files(webapp, p -> p.toString().endsWith(".jsp"));
        List<JspItem> jsps = new ArrayList<>();
        for (Path p : jspFiles) {
            JspScanner.Result r = JspScanner.scan(Files.readString(p, StandardCharsets.UTF_8));
            String name = p.getFileName().toString();
            jsps.add(new JspItem(SourceTree.rel(webapp, p), modules.forJsp(name), r.lines(), r.scriptlets(),
                    r.expressions(), r.declarations(), r.javaxByPackage().values().stream().mapToInt(Integer::intValue).sum()));
        }

        // ---- tiles
        List<TileItem> tileItems = new ArrayList<>();
        for (TilesDefs.Definition d : tiles.definitions().values()) {
            tileItems.add(new TileItem(d.name(), d.parent(), d.layout(), tiles.resolveBody(d.name())));
        }
        tileItems.sort((a, b) -> a.name().compareTo(b.name()));

        // ---- actions
        List<String> unresolvedTiles = new ArrayList<>();
        List<String> missingClasses = new ArrayList<>();
        List<String> unknownRoles = new ArrayList<>();
        List<ActionItem> actions = new ArrayList<>();
        for (StrutsConfig.Action a : struts.actions()) {
            List<ForwardItem> forwards = new ArrayList<>();
            for (StrutsConfig.Forward f : a.forwards()) {
                String kind = kindOf(f.path());
                String body = "tile".equals(kind) ? tiles.resolveBody(f.path()) : null;
                if ("tile".equals(kind) && body == null) {
                    unresolvedTiles.add(a.path() + " -> " + f.path());
                }
                forwards.add(new ForwardItem(f.name(), f.path(), kind, f.redirect(), body));
            }
            String inputKind = a.input() == null ? null : kindOf(a.input());
            if ("tile".equals(inputKind) && tiles.resolveBody(a.input()) == null) {
                unresolvedTiles.add(a.path() + " input -> " + a.input());
            }
            if (a.parameter() != null && kindOf(a.parameter()).equals("tile") && tiles.resolveBody(a.parameter()) == null) {
                unresolvedTiles.add(a.path() + " parameter -> " + a.parameter());
            }
            if (!a.type().startsWith("org.apache.struts.") && !knownClasses.contains(a.type())) {
                missingClasses.add(a.path() + " -> " + a.type());
            }
            if (a.rolesDeclared() != null) {
                for (String r : a.rolesDeclared()) {
                    if (!roles.contains(r)) {
                        unknownRoles.add(a.path() + " -> " + r);
                    }
                }
            }
            String kind = a.type().endsWith(".ForwardAction") ? "ForwardAction" : "Action";
            actions.add(new ActionItem(a.path(), modules.forActionPath(a.path()), a.type(), kind, a.form(), a.scope(),
                    a.validate(), a.input(), inputKind, a.parameter(), a.rolesDeclared(), forwards,
                    modules.inMigrationScope(a.path())));
        }
        actions.sort((x, y) -> x.path().compareTo(y.path()));
        unresolvedTiles.sort(String::compareTo);
        missingClasses.sort(String::compareTo);
        unknownRoles.sort(String::compareTo);

        // ---- modules
        List<ModuleItem> moduleItems = new ArrayList<>();
        for (String m : modules.names()) {
            int acts = (int) actions.stream().filter(a -> a.module().equals(m)).count();
            List<JavaItem> mj = javaMain.stream().filter(j -> j.module().equals(m)).toList();
            List<JspItem> mjsp = jsps.stream().filter(j -> j.module().equals(m)).toList();
            moduleItems.add(new ModuleItem(m, acts, mjsp.size(), mj.size(), mj.stream().mapToInt(JavaItem::lines).sum(),
                    (int) mj.stream().filter(j -> j.file().endsWith("Dao.java")).count(),
                    (int) mj.stream().filter(j -> j.file().endsWith("Manager.java")).count(),
                    (int) mj.stream().filter(j -> j.file().endsWith("Action.java")).count(),
                    (int) mj.stream().filter(j -> j.file().endsWith("Form.java")).count(),
                    (int) javaTest.stream().filter(j -> j.module().equals(m)).count(),
                    mjsp.stream().mapToInt(j -> j.scriptlets() + j.expressions() + j.declarations()).sum()));
        }

        // ---- summary
        Map<String, Object> summary = new TreeMap<>();
        summary.put("actions", actions.size());
        summary.put("forwardActions", actions.stream().filter(a -> a.kind().equals("ForwardAction")).count());
        summary.put("distinctCustomActionClasses", actions.stream().map(ActionItem::type)
                .filter(t -> !t.startsWith("org.apache.struts.")).distinct().count());
        summary.put("actionsWithRolesDeclared", actions.stream().filter(a -> a.rolesDeclared() != null).count());
        summary.put("actionsWithoutRolesAttribute", actions.stream().filter(a -> a.rolesDeclared() == null).count());
        summary.put("formBeans", struts.formBeans().size());
        summary.put("filters", web.filtersInOrder().size());
        summary.put("servlets", web.servlets().size());
        summary.put("listeners", web.listeners().size());
        summary.put("jsps", jsps.size());
        summary.put("jspScriptlets", jsps.stream().mapToInt(JspItem::scriptlets).sum());
        summary.put("jspExpressions", jsps.stream().mapToInt(JspItem::expressions).sum());
        summary.put("jspDeclarations", jsps.stream().mapToInt(JspItem::declarations).sum());
        summary.put("jspScriptletBlocks", jsps.stream().mapToInt(j -> j.scriptlets() + j.expressions() + j.declarations()).sum());
        summary.put("tilesDefinitions", tileItems.size());
        summary.put("roles", roles.size());
        summary.put("javaMainFiles", javaMain.size());
        summary.put("javaMainLines", javaMain.stream().mapToInt(JavaItem::lines).sum());
        summary.put("javaTestFiles", javaTest.size());
        summary.put("daos", javaMain.stream().filter(j -> j.file().endsWith("Dao.java")).count());
        summary.put("managers", javaMain.stream().filter(j -> j.file().endsWith("Manager.java")).count());

        List<Integration> integrations = integrations(deps, web);

        // ---- fingerprint of every input
        List<String> inputs = new ArrayList<>();
        for (String f : new String[] {"pom.xml", "db/V2__seed.sql", "src/main/webapp/META-INF/context.xml"}) {
            inputs.add(f + ":" + Hashing.sha256(root.resolve(f)));
        }
        for (Path p : SourceTree.files(webapp, x -> true)) {
            inputs.add("webapp/" + SourceTree.rel(webapp, p) + ":" + Hashing.sha256(p));
        }
        for (Path p : mainJava) {
            inputs.add(SourceTree.rel(root, p) + ":" + Hashing.sha256(p));
        }
        for (Path p : testJava) {
            inputs.add(SourceTree.rel(root, p) + ":" + Hashing.sha256(p));
        }

        Map<String, Object> findings = new TreeMap<>();
        findings.put("unresolvedTiles", unresolvedTiles);
        findings.put("missingActionClasses", missingClasses);
        findings.put("unknownRoles", unknownRoles);

        Map<String, Object> sessionConfig = new TreeMap<>();
        sessionConfig.put("timeoutMinutes", web.sessionTimeoutMinutes());
        sessionConfig.put("trackingModes", web.trackingModes());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schema", 1);
        out.put("tool", TOOL);
        out.put("source", "legacy-app");
        out.put("inputsFingerprint", Hashing.fingerprint(inputs));
        out.put("summary", summary);
        out.put("modules", moduleItems);
        out.put("actions", actions);
        out.put("formBeans", struts.formBeans());
        out.put("filtersInOrder", web.filtersInOrder());
        out.put("servlets", web.servlets());
        out.put("listeners", web.listeners());
        out.put("errorPages", web.errorPages());
        out.put("resourceRefs", web.resourceRefs());
        out.put("sessionConfig", sessionConfig);
        out.put("webXmlNamespace", web.namespace());
        out.put("webXmlVersion", web.version());
        out.put("tilesDefinitions", tileItems);
        out.put("jsps", jsps);
        out.put("javaMain", javaMain);
        out.put("roles", roles);
        out.put("integrations", integrations);
        out.put("findings", findings);
        out.put("notes", List.of(
                "Counting rule for actions: every <action> element in struts-config.xml, ForwardAction included.",
                "Counting rule for JSPs: every *.jsp under src/main/webapp.",
                "rolesDeclared is null when the roles attribute is absent and a list (possibly empty) when present.",
                "Forwards built in Java code (new ActionForward(...)) are not visible to this inventory."));
        return out;
    }

    static String kindOf(String path) {
        String p = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        if (p.endsWith(".do")) {
            return "action";
        }
        if (p.endsWith(".jsp")) {
            return "jsp";
        }
        return "tile";
    }

    private static List<Integration> integrations(List<PomReader.Dependency> deps, WebXml.Parsed web) {
        List<Integration> out = new ArrayList<>();
        for (PomReader.Dependency d : deps) {
            String c = d.coordinates();
            if (c.equals("org.apache.struts:struts-core")) {
                out.add(new Integration("Struts 1 MVC", "pom: " + c + " " + d.version()));
            } else if (c.equals("org.apache.struts:struts-tiles")) {
                out.add(new Integration("Tiles 1 page layout", "pom: " + c + " " + d.version()));
            } else if (c.equals("org.quartz-scheduler:quartz")) {
                out.add(new Integration("Quartz scheduler (nightly batch)", "pom: " + c + " " + d.version()));
            } else if (c.equals("com.lowagie:itext")) {
                out.add(new Integration("iText PDF export", "pom: " + c + " " + d.version()));
            } else if (c.equals("log4j:log4j")) {
                out.add(new Integration("log4j 1.x logging", "pom: " + c + " " + d.version()));
            } else if (c.equals("org.postgresql:postgresql")) {
                out.add(new Integration("PostgreSQL JDBC driver (container-provided)", "pom: " + c + " " + d.version()
                        + " scope " + d.scope()));
            }
        }
        for (WebXml.ResourceRef r : web.resourceRefs()) {
            out.add(new Integration("JNDI DataSource", "web.xml resource-ref " + r.name()));
        }
        out.sort((a, b) -> a.name().compareTo(b.name()));
        return out;
    }
}
