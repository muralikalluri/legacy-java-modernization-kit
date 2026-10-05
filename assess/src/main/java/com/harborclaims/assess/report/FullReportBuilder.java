package com.harborclaims.assess.report;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.samskivert.mustache.Mustache;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds ASSESSMENT_AND_ROADMAP.md. It starts from the quick assessment's view model, so the two documents can never
 * disagree, and adds the sections only the full report has. Like the quick report, the template holds no digits.
 */
public final class FullReportBuilder {

    public record Mapping(String legacy, String target, String notes, String count) {
    }

    private record MappingFile(List<Mapping> mappings) {
    }

    private FullReportBuilder() {
    }

    public static List<Mapping> loadMapping(Path yaml) throws IOException {
        return new ObjectMapper(new YAMLFactory()).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(Files.readAllBytes(yaml), MappingFile.class).mappings();
    }

    public static String build(String template, Map<String, Object> quick, Map<String, Object> risksJson, List<Mapping> mapping,
            JsonNode inventory, JsonNode deps, JsonNode jakarta, JsonNode structure, JsonNode security, JsonNode estimate) {
        Map<String, Object> v = new LinkedHashMap<>(quick);

        // ---- executive summary
        int migratedActions = 0;
        for (JsonNode a : inventory.get("actions")) {
            if (a.get("migrationScope").asBoolean()) {
                migratedActions++;
            }
        }
        v.put("provenNote", "The first slice is not a plan on paper. " + Md.plural(migratedActions, "action", "actions")
                + " (every Customers action and the claim search) already run in a Spring Boot 3 app behind a gateway, next to the unchanged legacy app,"
                + " and the same characterisation suite passes against both. See `docs/adr/0004-characterization-contract.md` and `docs/adr/0005-auth-bridge-and-strangler-gateway.md`.");

        // ---- inventory
        List<Map<String, Object>> roleRows = new ArrayList<>();
        for (JsonNode role : inventory.get("roles")) {
            int n = 0;
            for (JsonNode a : inventory.get("actions")) {
                if (a.get("rolesDeclared").isArray() && contains(a.get("rolesDeclared"), role.asText())) {
                    n++;
                }
            }
            roleRows.add(row("role", Md.lower(role.asText()), "actions", n));
        }
        v.put("roleRows", roleRows);
        v.put("noRolesNote", Md.plural(inventory.get("summary").get("actionsWithoutRolesAttribute").asInt(), "mapping has", "mappings have")
                + " no roles attribute. That means three different things (public page, any signed-in user, or a flaw); only the admin mappings are judged as flaws.");
        List<Map<String, Object>> integrations = new ArrayList<>();
        for (JsonNode i : inventory.get("integrations")) {
            integrations.add(row("name", Md.text(i.get("name").asText()), "evidence", Md.text(i.get("evidence").asText())));
        }
        v.put("integrations", integrations);

        // ---- risk register
        List<Map<String, Object>> register = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> raw = (List<Map<String, Object>>) risksJson.get("register");
        for (Map<String, Object> r : raw) {
            register.add(row("rank", r.get("rank"), "group", r.get("group"), "title", Md.text((String) r.get("title")), "evidence",
                    Md.text((String) r.get("evidence")), "likelihood", r.get("likelihood"), "impact", r.get("impact"), "score", r.get("score"),
                    "basis", (Boolean) r.get("assumed") ? "assumption" : "measured"));
        }
        v.put("register", register);
        v.put("registerNote", "Likelihood and impact run from one to five and are judgements, kept with a rationale in `assess/reference/risk-rules.yaml`."
                + " Counts and evidence are measured from the code. Entries marked assumption are not measured and need confirming with the client.");

        // ---- framework mapping
        List<Map<String, Object>> mapRows = new ArrayList<>();
        for (Mapping m : mapping) {
            mapRows.add(row("legacy", Md.text(m.legacy()), "target", Md.text(m.target()), "found", count(m.count(), inventory, jakarta),
                    "notes", Md.text(m.notes())));
        }
        v.put("mapping", mapRows);

        // ---- module scoring
        List<String> first = new ArrayList<>();
        List<String> later = new ArrayList<>();
        List<String> alone = new ArrayList<>();
        for (JsonNode s : estimate.get("moduleScores")) {
            String reason = s.get("module").asText() + ": business value " + s.get("businessValue").asInt() + ", coupling "
                    + Md.lower(s.get("couplingBand").asText()) + " (" + s.get("couplingPerClass").asText() + " cross-module edges per class), "
                    + Md.plural(s.get("riskSites").asInt(), "seeded risk site", "seeded risk sites") + ".";
            switch (s.get("decision").asText()) {
                case "MIGRATE_FIRST" -> first.add(reason);
                case "MIGRATE_LATER" -> later.add(reason);
                default -> alone.add(reason);
            }
        }
        v.put("migrateFirst", first);
        v.put("migrateLater", later);
        v.put("leaveAlone", alone);
        List<Map<String, Object>> structRows = new ArrayList<>();
        for (JsonNode m : structure.get("modules")) {
            structRows.add(row("name", m.get("name").asText(), "classes", m.get("classes").asInt(), "lines", m.get("lines").asInt(), "fanIn",
                    m.get("fanIn").asInt(), "fanOut", m.get("fanOut").asInt(), "instability", m.get("instabilityPercent").asInt()));
        }
        v.put("structureRows", structRows);
        List<String> cycles = new ArrayList<>();
        for (JsonNode c : structure.get("moduleCycles")) {
            cycles.add("Module cycle: " + String.join(", ", strings(c.get("members"))) + ".");
        }
        for (JsonNode c : structure.get("packageCycles")) {
            List<String> shortNames = new ArrayList<>();
            strings(c.get("members")).forEach(p -> shortNames.add(p.substring(p.lastIndexOf('.') + 1)));
            cycles.add("Package cycle: " + String.join(", ", shortNames) + ", with " + Md.plural(c.get("witnesses").size(), "witness edge", "witness edges") + ".");
        }
        v.put("cycles", cycles);
        List<Map<String, Object>> biggest = new ArrayList<>();
        int shown = 0;
        for (JsonNode c : structure.get("largestClasses")) {
            if (shown++ == 5) {
                break;
            }
            biggest.add(row("name", Md.shortName(c.get("className").asText()), "module", c.get("module").asText(), "lines", c.get("lines").asInt(),
                    "god", c.get("godClass").asBoolean() ? "yes" : "no"));
        }
        v.put("biggest", biggest);
        v.put("godThreshold", structure.get("parameters").get("godClassThresholdLines").asInt());

        // ---- target architecture
        v.put("architectureDiagram", architecture());
        List<String> archNotes = new ArrayList<>();
        archNotes.add("The gateway matches `/customers/` as a prefix and `/claims/search.do` exactly. Everything else goes to the legacy app, so a path nobody migrated keeps working.");
        archNotes.add("Legacy is the only place anyone signs in. It issues a signed cookie; the Spring Boot app verifies it and reads the role from the database on every request, never from the cookie.");
        archNotes.add("Both apps share one PostgreSQL schema at first. Splitting the data per module is a later step, taken only when a module has moved completely, because a shared schema is what makes the first slice cheap and safe.");
        v.put("architectureNotes", archNotes);

        // ---- roadmap
        List<Map<String, Object>> roadmap = new ArrayList<>();
        StringBuilder flow = new StringBuilder("flowchart LR\n");
        String prev = null;
        for (JsonNode p : estimate.get("phases")) {
            int id = p.get("id").asInt();
            List<String> mods = new ArrayList<>();
            for (JsonNode it : p.get("items")) {
                String m = it.get("module").asText();
                if (!mods.contains(m)) {
                    mods.add(m);
                }
            }
            String effort = Md.range(dec(p.get("total").get("low")), dec(p.get("total").get("high")));
            roadmap.add(row("id", id, "name", Md.text(p.get("name").asText()), "goal", phaseGoal(id), "exit", phaseExit(id), "modules", String.join(", ", mods),
                    "effort", effort));
            String node = "P" + id;
            flow.append("  ").append(node).append("[\"Phase ").append(id).append(": ").append(p.get("name").asText().replace("\"", "'")).append("\"]\n");
            if (prev != null) {
                flow.append("  ").append(prev).append(" --> ").append(node).append("\n");
            }
            prev = node;
        }
        v.put("roadmap", roadmap);
        v.put("phaseFlow", flow.toString().stripTrailing());

        // ---- effort
        Map<String, BigDecimal[]> byModule = new LinkedHashMap<>();
        List<Map<String, Object>> byModulePhase = new ArrayList<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode p : estimate.get("phases")) {
            Map<String, BigDecimal[]> inPhase = new LinkedHashMap<>();
            for (JsonNode it : p.get("items")) {
                BigDecimal lo = dec(it.get("effort").get("low"));
                BigDecimal hi = dec(it.get("effort").get("high"));
                add(byModule, it.get("module").asText(), lo, hi);
                add(inPhase, it.get("module").asText(), lo, hi);
                items.add(row("phase", p.get("id").asInt(), "module", it.get("module").asText(), "kind", Md.lower(it.get("kind").asText()),
                        "description", Md.text(it.get("description").asText()), "quantity", it.get("quantity").asText(),
                        "effort", Md.range(lo, hi)));
            }
            inPhase.forEach((m, e) -> byModulePhase.add(row("phase", p.get("id").asInt(), "module", m, "effort", Md.range(e[0], e[1]))));
        }
        List<Map<String, Object>> moduleRows = new ArrayList<>();
        byModule.forEach((m, e) -> moduleRows.add(row("module", m, "effort", Md.range(e[0], e[1]))));
        v.put("moduleEffort", moduleRows);
        v.put("modulePhaseEffort", byModulePhase);
        v.put("lineItems", items);
        List<Map<String, Object>> sens = new ArrayList<>();
        int n = 0;
        for (JsonNode s : estimate.get("sensitivity")) {
            if (n++ == 5) {
                break;
            }
            sens.add(row("parameter", s.get("parameter").asText(), "half", signed(dec(s.get("halfEffectOnMidpoint"))), "oneAndHalf",
                    signed(dec(s.get("oneAndHalfEffectOnMidpoint")))));
        }
        v.put("sensitivity", sens);
        JsonNode alt = estimate.get("alternativePerMapping");
        v.put("alternativeNote", "Charging the action-class rate per mapping instead of per distinct class would give "
                + Md.range(dec(alt.get("totalLow")), dec(alt.get("totalHigh"))) + " ideal person-days in total, a difference of "
                + Md.range(dec(alt.get("deltaLow")), dec(alt.get("deltaHigh"))) + ".");
        v.put("teamShape", "Calendar time assumes " + Md.plural(estimate.get("totals").get("developers").asInt(), "developer", "developers")
                + ". A workable split: one engineer owns Phase 0, the gateway, the auth bridge and the characterisation harness; the other migrates module"
                + " by module behind those tests. Someone on the client side must be reachable to answer business-rule questions, because the rules live only in code.");

        // ---- security gaps
        List<Map<String, Object>> gaps = new ArrayList<>();
        for (JsonNode f : security.get("findings")) {
            String cat = f.get("category").asText();
            gaps.add(row("title", Md.text(f.get("title").asText()), "cwe", f.get("cwe").asText(), "count", Md.unit(f.get("count").asInt(), f.get("countUnit").asText()),
                    "phaseZero", phaseZeroFix(cat), "modern", modernFix(cat)));
        }
        v.put("gaps", gaps);
        v.put("inherited", "Inherited, not fixed: the legacy admin flaws stay visible through the gateway (any signed-in user can create users and run the batch job)."
                + " The new app neither weakens nor hides them; they belong to Phase 0.");

        // ---- appendix
        List<Map<String, Object>> actions = new ArrayList<>();
        for (JsonNode a : inventory.get("actions")) {
            actions.add(row("path", a.get("path").asText(), "module", a.get("module").asText(), "class", Md.shortName(a.get("type").asText()),
                    "roles", a.get("rolesDeclared").isArray() ? Md.lower(String.join(", ", strings(a.get("rolesDeclared")))) : "none declared",
                    "migrated", a.get("migrationScope").asBoolean() ? "yes" : "no"));
        }
        v.put("actions", actions);
        List<Map<String, Object>> libs = new ArrayList<>();
        for (JsonNode l : deps.get("libraries")) {
            libs.add(row("file", Md.text(l.get("file").asText()), "osv", Md.lower(l.get("osvStatus").asText()), "advisories", l.get("advisoryIds").size(),
                    "eol", l.get("eol").isNull() ? "none" : Md.lower(l.get("eol").get("status").asText())));
        }
        v.put("libraries", libs);
        JsonNode main = jakarta.get("javaMain");
        List<Map<String, Object>> pkgs = new ArrayList<>();
        main.get("importsByPackage").fields().forEachRemaining(e -> pkgs.add(row("pkg", e.getKey(), "imports", e.getValue().asInt(),
                "moves", e.getKey().startsWith("javax.servlet") ? "yes" : "no")));
        v.put("javaxPackages", pkgs);
        v.put("rewriteNote", "The OpenRewrite dry run (" + jakarta.get("rewrite").get("recipe").asText() + ") would change "
                + Md.plural(jakarta.get("rewrite").get("changedFiles").asInt(), "file", "files") + ". The patch is `assess/results/" + jakarta.get("rewrite").get("patchFile").asText()
                + "`, sha256 " + jakarta.get("rewrite").get("patchSha256").asText().substring(0, 12) + ". No source file was modified.");
        List<Map<String, Object>> outputs = new ArrayList<>();
        String[][] files = {{"inventory.json", "assess inventory", "Actions, forms, forwards, filters, JSPs, scriptlets, roles"},
            {"deps.json", "assess deps", "Libraries in the WAR, advisories from OSV, end-of-life flags"},
            {"jakarta.json", "assess jakarta", "javax imports by package and the OpenRewrite dry run summary"},
            {"jakarta-dryrun.patch", "assess jakarta", "The dry run diff itself"},
            {"structure.json", "assess structure", "Cycles, largest classes, fan-in and fan-out per module"},
            {"security.json", "assess security", "Semgrep findings cross-referenced with the inventory"},
            {"estimate.json", "assess estimate", "Effort bands per module and phase, scenarios, sensitivity"},
            {"risks.json", "assess report", "The full risk register behind the top-ten table"}};
        JsonNode[] sources = {inventory, deps, jakarta, null, structure, security, estimate, null};
        for (int i = 0; i < files.length; i++) {
            JsonNode s = sources[i];
            String fp = s != null && s.has("inputsFingerprint") ? s.get("inputsFingerprint").asText().substring(0, 12) : "n/a";
            outputs.add(row("file", files[i][0], "command", files[i][1], "contains", files[i][2], "fingerprint", fp));
        }
        v.put("outputs", outputs);

        String markdown = Mustache.compiler().escapeHTML(false).compile(template).execute(v);
        return markdown.endsWith("\n") ? markdown : markdown + "\n";
    }


    public static final String README_START = "<!-- results:start (generated by assess report, do not edit) -->";
    public static final String README_END = "<!-- results:end -->";

    /** The evidence table for the README. Every value is read from a results file; the last column names which. */
    public static String readmeResults(JsonNode inventory, JsonNode deps, JsonNode jakarta, JsonNode structure, JsonNode security, JsonNode estimate) {
        JsonNode inv = inventory.get("summary");
        JsonNode dsum = deps.get("summary");
        JsonNode sev = dsum.get("uniqueAdvisoriesBySeverity");
        JsonNode st = structure.get("summary");
        JsonNode t = estimate.get("totals");
        int sites = 0;
        for (JsonNode f : security.get("findings")) {
            sites += f.get("count").asInt();
        }
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"System size", Md.plural(inv.get("actions").asInt(), "Struts action", "Struts actions") + ", " + inv.get("jsps").asInt() + " JSPs, "
                + inv.get("javaMainFiles").asInt() + " Java files, " + inv.get("javaMainLines").asInt() + " lines", "inventory.json"});
        rows.add(new String[] {"Seeded security findings", Md.plural(sites, "site", "sites") + " in " + Md.plural(security.get("findings").size(), "category", "categories"),
                "security.json"});
        rows.add(new String[] {"Vulnerable dependencies", Md.plural(dsum.get("uniqueAdvisoriesInWar").asInt(), "advisory", "advisories") + " in the WAR, "
                + Md.plural(dsum.get("eolFlaggedLibraries").asInt(), "library", "libraries") + " end of life, unmaintained or superseded ("
                + sev.get("CRITICAL").asInt() + " critical and " + sev.get("HIGH").asInt() + " high advisories across the WAR and the container)", "deps.json"});
        rows.add(new String[] {"Structure", Md.plural(st.get("godClasses").asInt(), "God class", "God classes") + " (" + st.get("largestClassLines").asInt() + " lines), "
                + Md.plural(st.get("packageCycles").asInt(), "package cycle", "package cycles"), "structure.json"});
        rows.add(new String[] {"javax to jakarta", jakarta.get("rewrite").get("changedFiles").asInt() + " files change in an OpenRewrite dry run; "
                + jakarta.get("javaMain").get("jakartaMigratingImports").asInt() + " imports move", "jakarta.json"});
        rows.add(new String[] {"Estimated effort", Md.range(dec(t.get("low")), dec(t.get("high"))) + " " + t.get("unit").asText() + " ("
                + Md.range(dec(t.get("calendarWeeksLow")), dec(t.get("calendarWeeksHigh"))) + " calendar weeks, "
                + Md.plural(t.get("developers").asInt(), "developer", "developers") + ")", "estimate.json"});
        StringBuilder sb = new StringBuilder(README_START).append("\n\n| Result | Value | Source |\n|---|---|---|\n");
        for (String[] r : rows) {
            sb.append("| ").append(r[0]).append(" | ").append(r[1]).append(" | `assess/results/").append(r[2]).append("` |\n");
        }
        return sb.append("\n").append(README_END).toString();
    }

    /** Replaces the block between the markers; fails if the markers are missing so the README cannot silently go stale. */
    public static String replaceReadmeBlock(String readme, String block) {
        int a = readme.indexOf(README_START);
        int b = readme.indexOf(README_END);
        if (a < 0 || b < a) {
            throw new IllegalArgumentException("README.md has no results block: add the start and end markers");
        }
        return readme.substring(0, a) + block + readme.substring(b + README_END.length());
    }

    private static String architecture() {
        return String.join("\n",
                "flowchart TD",
                "  user([\"Browser\"]) --> gw[\"Gateway (nginx)\"]",
                "  gw -- \"/customers/ and /claims/search.do\" --> modern[\"Spring Boot 3 app, Java 21\"]",
                "  gw -- \"everything else\" --> legacy[\"Legacy Struts WAR, Java 8\"]",
                "  legacy -. \"signs the HARBOR_AUTH cookie\" .-> modern",
                "  modern --> db[(\"PostgreSQL, one shared schema\")]",
                "  legacy --> db",
                "  db -. \"later: one schema per migrated module\" .-> split[(\"Per-module schemas\")]");
    }

    private static String phaseGoal(int id) {
        return switch (id) {
            case 0 -> "Close the seeded security flaws in the legacy app before anything is built next to it, so the migration starts from a defensible base.";
            case 1 -> "Stand up the platform: gateway, auth bridge, the Spring Boot app, Flyway baseline, and the characterisation harness. Move the first slice through it.";
            default -> "Move the next module behind the gateway, one slice at a time, behind characterisation tests.";
        };
    }

    private static String phaseExit(int id) {
        return switch (id) {
            case 0 -> "The security scan reports no sites for the fixed categories and the legacy smoke test is green.";
            case 1 -> "The characterisation suite passes unchanged against legacy and through the gateway.";
            default -> "The same suite passes through the gateway for the module's routes, then the legacy code for it is removed.";
        };
    }

    private static String phaseZeroFix(String category) {
        return switch (category) {
            case "SQL_INJECTION" -> "Bind parameters in the affected statements.";
            case "CSRF" -> "Add a token to every state-changing form and check it in one place; make state-changing links POST.";
            case "PLAINTEXT_PASSWORD" -> "Hash with bcrypt, migrate stored passwords at next login, force a reset for the rest.";
            case "MISSING_AUTHORIZATION" -> "Add the roles attribute to the unprotected mappings.";
            case "SESSION_FIXATION" -> "Invalidate and recreate the session at login.";
            default -> "Fix in place.";
        };
    }

    private static String modernFix(String category) {
        return switch (category) {
            case "SQL_INJECTION" -> "Gone by construction: every statement in the new app uses bound parameters.";
            case "CSRF" -> "A token bound to the login on every form, checked for every state-changing request.";
            case "PLAINTEXT_PASSWORD" -> "Not yet: sign-in stays in the legacy app until the credential migration is done.";
            case "MISSING_AUTHORIZATION" -> "Role rules in the filter chain, with method security on the services as a second line.";
            case "SESSION_FIXATION" -> "The new app has no HTTP session at all; identity is a signed, revocable cookie.";
            default -> "Handled in the new code.";
        };
    }

    private static String count(String key, JsonNode inventory, JsonNode jakarta) {
        JsonNode s = inventory.get("summary");
        return switch (key) {
            case "ACTION_CLASSES" -> Md.plural(s.get("distinctCustomActionClasses").asInt(), "action class", "action classes") + " for "
                    + Md.plural(s.get("actions").asInt(), "mapping", "mappings");
            case "FORM_BEANS" -> Md.plural(s.get("formBeans").asInt(), "form bean", "form beans");
            case "TILES" -> Md.plural(s.get("tilesDefinitions").asInt(), "Tiles definition", "Tiles definitions") + ", "
                    + Md.plural(s.get("jspScriptletBlocks").asInt(), "scriptlet block", "scriptlet blocks");
            case "DAOS" -> Md.plural(s.get("daos").asInt(), "DAO", "DAOs");
            case "MANAGERS" -> Md.plural(s.get("managers").asInt(), "manager", "managers");
            case "RESOURCE_REFS" -> Md.plural(inventory.get("resourceRefs").size(), "JNDI resource", "JNDI resources");
            case "FILTERS" -> Md.plural(s.get("filters").asInt(), "filter", "filters");
            case "ROLES" -> Md.plural(s.get("roles").asInt(), "role", "roles");
            case "LISTENERS" -> Md.plural(s.get("listeners").asInt(), "listener that starts the scheduler", "listeners that start the scheduler");
            case "JAKARTA_IMPORTS" -> Md.plural(jakarta.get("javaMain").get("jakartaMigratingImports").asInt(), "import that moves", "imports that move") + " in "
                    + Md.plural(jakarta.get("javaMain").get("filesWithJakartaMigratingJavax").asInt(), "file", "files");
            case "NONE" -> "not counted";
            default -> throw new IllegalArgumentException("framework-mapping.yaml: unknown count key " + key);
        };
    }

    private static boolean contains(JsonNode array, String value) {
        for (JsonNode n : array) {
            if (n.asText().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    private static void add(Map<String, BigDecimal[]> m, String key, BigDecimal lo, BigDecimal hi) {
        BigDecimal[] cur = m.computeIfAbsent(key, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
        cur[0] = cur[0].add(lo);
        cur[1] = cur[1].add(hi);
    }

    private static String signed(BigDecimal v) {
        return (v.signum() > 0 ? "+" : "") + Md.num(v);
    }

    private static BigDecimal dec(JsonNode n) {
        return n.decimalValue();
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
