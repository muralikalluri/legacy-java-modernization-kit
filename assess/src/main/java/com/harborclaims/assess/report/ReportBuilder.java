package com.harborclaims.assess.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.samskivert.mustache.Mustache;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Builds the quick assessment: a view model of pre-formatted strings from the results, rendered through a template. */
public final class ReportBuilder {

    public static final String APP_NAME = "Harbor Claims Portal (legacy Java)";
    public static final String DISCLAIMER = "Fictional demo system.";

    public record Output(String markdown, Map<String, Object> risksJson, Map<String, Object> viewModel) {
    }

    private ReportBuilder() {
    }

    public static Output build(String template, RiskRules rules, JsonNode inventory, JsonNode deps, JsonNode jakarta,
            JsonNode structure, JsonNode security, JsonNode estimate) {
        List<RiskBuilder.Risk> register = RiskBuilder.register(rules, security, deps, structure, jakarta);
        RiskBuilder.Selection sel = RiskBuilder.top(register, rules);

        Map<String, Object> v = new LinkedHashMap<>();
        JsonNode inv = inventory.get("summary");
        JsonNode dsum = deps.get("summary");
        JsonNode totals = estimate.get("totals");

        v.put("appName", APP_NAME);
        v.put("disclaimer", DISCLAIMER);
        v.put("dataAsOf", deps.get("provenance").get("dataAsOf").asText());
        v.put("tool", inventory.get("tool").asText());
        v.put("fingerprint", estimate.get("inputsFingerprint").asText().substring(0, 12));
        v.put("semgrepVersion", security.get("semgrep").get("version").asText());
        v.put("eolAsOf", deps.get("provenance").get("eolTableAsOf").asText());

        // ---- inventory
        List<Map<String, Object>> modules = new ArrayList<>();
        int actions = 0;
        int jsps = 0;
        int files = 0;
        int lines = 0;
        int blocks = 0;
        for (JsonNode m : inventory.get("modules")) {
            int b = m.get("scriptletBlocks").asInt();
            modules.add(row("name", m.get("name").asText(), "actions", m.get("actions").asInt(), "jsps", m.get("jsps").asInt(),
                    "javaFiles", m.get("javaMainFiles").asInt(), "javaLines", m.get("javaMainLines").asInt(), "scriptletBlocks", b));
            actions += m.get("actions").asInt();
            jsps += m.get("jsps").asInt();
            files += m.get("javaMainFiles").asInt();
            lines += m.get("javaMainLines").asInt();
            blocks += b;
        }
        v.put("modules", modules);
        v.put("totalLabel", "All modules");
        v.put("totalActions", actions);
        v.put("totalJsps", jsps);
        v.put("totalJavaFiles", files);
        v.put("totalJavaLines", lines);
        v.put("totalScriptletBlocks", blocks);

        List<String> roles = new ArrayList<>();
        inventory.get("roles").forEach(r -> roles.add(Md.lower(r.asText())));
        List<String> filters = new ArrayList<>();
        inventory.get("filtersInOrder").forEach(f -> filters.add(f.get("name").asText()));
        List<String> facts = new ArrayList<>();
        facts.add(Md.plural(inv.get("actions").asInt(), "action mapping", "action mappings") + " ("
                + inv.get("forwardActions").asInt() + " are plain forwards, "
                + Md.plural(inv.get("distinctCustomActionClasses").asInt(), "custom action class", "custom action classes") + "), "
                + Md.plural(inv.get("formBeans").asInt(), "form bean", "form beans") + " and "
                + Md.plural(inv.get("tilesDefinitions").asInt(), "Tiles definition", "Tiles definitions") + ".");
        facts.add(Md.plural(inv.get("jsps").asInt(), "JSP", "JSPs") + " with "
                + Md.plural(inv.get("jspScriptletBlocks").asInt(), "scriptlet block", "scriptlet blocks") + " ("
                + inv.get("jspScriptlets").asInt() + " scriptlets, " + inv.get("jspExpressions").asInt() + " expressions, "
                + inv.get("jspDeclarations").asInt() + " declarations).");
        facts.add(Md.plural(inv.get("roles").asInt(), "role", "roles") + " held in a database table: " + String.join(", ", roles) + ".");
        facts.add("Request filters, in order: " + String.join(", ", filters) + ". Roles are checked per action with the Struts roles attribute on "
                + inv.get("actionsWithRolesDeclared").asInt() + " of " + inv.get("actions").asInt() + " mappings.");
        List<String> integrations = new ArrayList<>();
        inventory.get("integrations").forEach(i -> integrations.add(i.get("name").asText()));
        facts.add(Md.plural(inv.get("daos").asInt(), "DAO", "DAOs") + " and " + Md.plural(inv.get("managers").asInt(), "manager", "managers")
                + " over plain JDBC. Integrations found: " + String.join(", ", integrations) + ".");
        v.put("facts", facts);
        v.put("summary", "The portal has " + Md.plural(actions, "Struts action", "Struts actions") + " across "
                + Md.plural(inventory.get("modules").size() - 1, "business module", "business modules") + " plus a shared platform, "
                + Md.plural(jsps, "JSP", "JSPs") + " and " + Md.plural(files, "Java file", "Java files") + " ("
                + lines + " lines). " + scoreSentence(estimate) + " " + effortSentence(estimate));

        // ---- risks
        List<Map<String, Object>> riskRows = new ArrayList<>();
        int rank = 1;
        for (RiskBuilder.Risk r : sel.top()) {
            riskRows.add(row("rank", rank++, "title", Md.text(r.title()), "evidence", Md.text(r.evidence()), "likelihood", r.likelihood(),
                    "impact", r.impact(), "score", r.score()));
        }
        v.put("risks", riskRows);
        v.put("topIntro", "Ranked by likelihood times impact, one entry per root cause. At most " + rules.perGroupCap()
                + " entries come from any one group, so the list is not just the worst CVEs. The full register has "
                + register.size() + " entries in `assess/results/risks.json`.");
        List<String> riskNotes = new ArrayList<>();
        if (!sel.omittedByCap().isEmpty()) {
            List<String> omittedTitles = new ArrayList<>();
            for (RiskBuilder.Risk r : register) {
                if (sel.omittedByCap().contains(r.id())) {
                    omittedTitles.add(r.title());
                }
            }
            riskNotes.add(Md.plural(sel.omittedByCap().size(), "risk was", "risks were") + " left out of this list by the per-group cap: "
                    + Md.text(String.join("; ", omittedTitles)) + ".");
        }
        if (sel.moreAtCutoffScore() > 0) {
            riskNotes.add(Md.plural(sel.moreAtCutoffScore(), "more risk", "more risks") + " share the score of the last entry.");
        }
        v.put("riskNotes", riskNotes);

        // ---- dependencies
        TreeSet<String> warCoords = new TreeSet<>();
        for (JsonNode l : deps.get("libraries")) {
            if (!l.get("groupId").isNull()) {
                warCoords.add(l.get("groupId").asText() + ":" + l.get("artifactId").asText() + ":" + l.get("version").asText());
            }
        }
        Map<String, Integer> warSeverity = new TreeMap<>();
        for (String s : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN")) {
            warSeverity.put(s, 0);
        }
        List<JsonNode> warAdvisories = new ArrayList<>();
        int providedAdvisories = 0;
        for (JsonNode a : deps.get("advisories")) {
            boolean inWar = false;
            for (JsonNode p : a.get("packages")) {
                if (warCoords.contains(p.asText())) {
                    inWar = true;
                }
            }
            if (inWar) {
                warSeverity.merge(a.get("severity").asText(), 1, Integer::sum);
                warAdvisories.add(a);
            } else {
                providedAdvisories++;
            }
        }
        List<Map<String, Object>> sevRows = new ArrayList<>();
        for (String s : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN")) {
            sevRows.add(row("name", s, "count", warSeverity.get(s)));
        }
        v.put("severities", sevRows);
        v.put("cveSummary", "The WAR holds " + Md.plural(dsum.get("jarsInWar").asInt(), "jar", "jars") + " ("
                + Md.plural(dsum.get("duplicateJars").asInt(), "duplicate", "duplicates") + "). OSV lists "
                + Md.plural(dsum.get("uniqueAdvisoriesInWar").asInt(), "unique advisory", "unique advisories") + " against "
                + Md.plural(dsum.get("librariesWithAdvisories").asInt(), "of its libraries", "of its libraries") + "; "
                + Md.plural(dsum.get("librariesWithNoOsvRecords").asInt(), "library has", "libraries have")
                + " no OSV records, which is not the same as clean. "
                + Md.plural(dsum.get("eolFlaggedLibraries").asInt(), "library is", "libraries are") + " end of life, unmaintained or superseded.");

        warAdvisories.sort(Comparator.comparing((JsonNode a) -> a.get("cvssScore").isNull() ? -1.0 : a.get("cvssScore").asDouble()).reversed()
                .thenComparing(a -> a.get("id").asText()));
        List<Map<String, Object>> topAdv = new ArrayList<>();
        for (JsonNode a : warAdvisories.subList(0, Math.min(5, warAdvisories.size()))) {
            List<String> libs = new ArrayList<>();
            a.get("packages").forEach(p -> libs.add(p.asText().substring(p.asText().indexOf(':') + 1)));
            topAdv.add(row("id", a.get("id").asText(), "library", Md.text(String.join(", ", libs)), "severity", a.get("severity").asText(),
                    "score", a.get("cvssScore").isNull() ? "n/a" : String.format(java.util.Locale.ROOT, "%.1f", a.get("cvssScore").asDouble()),
                    "summary", Md.text(a.get("summary").asText())));
        }
        v.put("topAdvisories", topAdv);

        List<Map<String, Object>> eol = new ArrayList<>();
        for (JsonNode l : deps.get("libraries")) {
            if (!l.get("eol").isNull()) {
                eol.add(row("library", l.get("artifactId").asText() + " " + l.get("version").asText(), "status", l.get("eol").get("status").asText(),
                        "since", l.get("eol").get("since").isNull() ? "n/a" : l.get("eol").get("since").asText()));
            }
        }
        v.put("eol", eol);
        TreeSet<String> providedNames = new TreeSet<>();
        for (JsonNode l : deps.get("provided")) {
            if (l.get("advisoryIds").size() > 0) {
                providedNames.add(l.get("artifactId").asText());
            }
        }
        v.put("providedNote", providedAdvisories == 0 ? "" : Md.plural(providedAdvisories, "further advisory", "further advisories")
                + " affect libraries the container provides (" + String.join(", ", providedNames)
                + "), which are not in the WAR and are counted separately.");

        // ---- approach
        JsonNode scen = estimate.get("scenarios");
        List<String> firstModules = new ArrayList<>();
        for (JsonNode s : estimate.get("moduleScores")) {
            if (s.get("decision").asText().equals("MIGRATE_FIRST")) {
                firstModules.add(s.get("module").asText());
            }
        }
        boolean strangler = !firstModules.isEmpty();
        JsonNode main = jakarta.get("javaMain");
        List<Map<String, Object>> approaches = new ArrayList<>();
        approaches.add(row("name", "Strangler fig behind a gateway", "verdict", strangler ? "Recommended" : "Possible",
                "effort", Md.range(dec(scen.get("strangler").get("low")), dec(scen.get("strangler").get("high"))),
                "why", "Legacy and Spring Boot 3 run side by side. Modules move one at a time behind characterisation tests, so risk is spread and nothing waits for a final cutover."));
        approaches.add(row("name", "Big-bang rewrite", "verdict", strangler ? "Not recommended" : "Possible",
                "effort", Md.range(dec(scen.get("bigBangRewrite").get("low")), dec(scen.get("bigBangRewrite").get("high"))),
                "why", "Its band also covers the modules the strangler plan leaves alone. For the same modules it is "
                        + Md.range(dec(scen.get("bigBangRewriteSameModules").get("low")), dec(scen.get("bigBangRewriteSameModules").get("high")))
                        + " on paper, because it skips the gateway, the bridge and the Phase 0 double work. The catch: every module, the God class and the cycles must be solved before anything ships, and nothing is released until it is all done."));
        approaches.add(row("name", "Lift: upgrade Java and the container in place", "verdict", "Blocked",
                "effort", "not applicable",
                "why", "Struts 1 has no Jakarta build. The OpenRewrite dry run would change " + jakarta.get("rewrite").get("changedFiles").asInt()
                        + " files, yet struts-core would still need javax.servlet (" + main.get("jakartaMigratingImports").asInt() + " imports in "
                        + main.get("filesWithJakartaMigratingJavax").asInt() + " files)."));
        v.put("approaches", approaches);
        v.put("recommendation", strangler
                ? "Use a strangler-fig migration. " + String.join(" and ", firstModules) + " can move first: coupling to the rest of the code is low, so it can be cut out"
                        + " with the least untangling. Fix the security findings in the legacy app first (Phase 0), then stand up the platform and move the first slice."
                : "No module has low enough coupling to move first. A rewrite behind characterisation tests is the fallback.");

        List<Map<String, Object>> scoreRows = new ArrayList<>();
        for (JsonNode s : estimate.get("moduleScores")) {
            scoreRows.add(row("module", s.get("module").asText(), "value", s.get("businessValue").asInt(), "coupling",
                    s.get("couplingPerClass").asText(), "band", s.get("couplingBand").asText(), "decision", decisionLabel(s.get("decision").asText())
                    + (s.get("inM6Scope").asBoolean() && !s.get("decision").asText().equals("MIGRATE_FIRST") ? " (first slice only)" : ""),
                    "phase", s.get("phase").isNull() ? "none" : "Phase " + s.get("phase").asInt()));
        }
        v.put("scores", scoreRows);
        List<String> notes = new ArrayList<>();
        estimate.get("moduleScores").forEach(s -> {
            if (!s.get("note").isNull()) {
                notes.add(s.get("module").asText() + ": " + s.get("note").asText());
            }
        });
        v.put("scoreNote", Md.text(String.join(" ", notes)) + " Business values are assumptions to confirm with the client.");

        // ---- effort
        v.put("effortSummary", effortSentence(estimate));
        List<Map<String, Object>> phases = new ArrayList<>();
        for (JsonNode p : estimate.get("phases")) {
            phases.add(row("id", p.get("id").asInt(), "name", Md.text(p.get("name").asText()), "effort",
                    Md.range(dec(p.get("total").get("low")), dec(p.get("total").get("high")))));
        }
        v.put("phases", phases);
        List<String> excluded = new ArrayList<>();
        for (JsonNode e : estimate.get("excludedModules")) {
            excluded.add(e.get("module").asText() + " (" + Md.range(dec(e.get("effort").get("low")), dec(e.get("effort").get("high"))) + ")");
        }
        v.put("excludedNote", excluded.isEmpty() ? "" : "Left alone and not in the total, with the cost of migrating them in brackets: "
                + String.join(", ", excluded) + ".");

        // ---- caveats and assumptions
        String caveat = "";
        for (JsonNode n : jakarta.get("notes")) {
            if (n.asText().startsWith("There is no Jakarta-compatible Struts 1")) {
                caveat = n.asText();
            }
        }
        v.put("caveat", Md.text(caveat));
        List<String> assumptions = new ArrayList<>();
        assumptions.add("Effort is in " + totals.get("unit").asText() + " as a low to high band. Bands add lows and highs separately: a hard band, not a confidence interval.");
        assumptions.add("Calendar time assumes " + Md.plural(totals.get("developers").asInt(), "developer", "developers") + " at a focus factor of "
                + totals.get("focusFactor").asText() + ".");
        assumptions.add("Unit rates, multipliers and business values are labelled assumptions in `assess/estimation-model.yaml`, each with a rationale. They are inputs, not measurements.");
        assumptions.add("Fixes made in the legacy app in Phase 0 are partly thrown away when the code is rewritten: deliberate double work.");
        v.put("assumptions", assumptions);

        String markdown = Mustache.compiler().escapeHTML(false).compile(template).execute(v);
        if (!markdown.endsWith("\n")) {
            markdown += "\n";
        }

        Map<String, Object> risksJson = new LinkedHashMap<>();
        risksJson.put("schema", 1);
        risksJson.put("tool", inventory.get("tool").asText());
        risksJson.put("topN", rules.topN());
        risksJson.put("perGroupCap", rules.perGroupCap());
        List<Map<String, Object>> reg = new ArrayList<>();
        int i = 1;
        for (RiskBuilder.Risk r : register) {
            reg.add(row("rank", i++, "id", r.id(), "group", r.group(), "title", r.title(), "evidence", r.evidence(), "likelihood",
                    r.likelihood(), "impact", r.impact(), "score", r.score(), "assumed", r.assumed()));
        }
        risksJson.put("register", reg);
        List<String> topIds = new ArrayList<>();
        sel.top().forEach(r -> topIds.add(r.id()));
        risksJson.put("topIds", topIds);
        risksJson.put("omittedByCap", sel.omittedByCap());
        risksJson.put("moreAtCutoffScore", sel.moreAtCutoffScore());
        return new Output(markdown, risksJson, v);
    }

    private static String scoreSentence(JsonNode estimate) {
        List<String> first = new ArrayList<>();
        List<String> alone = new ArrayList<>();
        for (JsonNode s : estimate.get("moduleScores")) {
            String d = s.get("decision").asText();
            if (d.equals("MIGRATE_FIRST")) {
                first.add(s.get("module").asText());
            } else if (d.equals("LEAVE_ALONE")) {
                alone.add(s.get("module").asText());
            }
        }
        return (first.isEmpty() ? "" : "Move " + String.join(", ", first) + " first. ")
                + (alone.isEmpty() ? "" : "Leave " + String.join(" and ", alone) + " alone.");
    }

    private static String effortSentence(JsonNode estimate) {
        JsonNode t = estimate.get("totals");
        List<String> alone = new ArrayList<>();
        estimate.get("excludedModules").forEach(e -> alone.add(e.get("module").asText()));
        return "Migrating the modules worth moving" + (alone.isEmpty() ? "" : " (" + String.join(" and ", alone) + " are left alone)")
                + " is roughly " + Md.range(dec(t.get("low")), dec(t.get("high"))) + " " + t.get("unit").asText() + ", about "
                + Md.range(dec(t.get("calendarWeeksLow")), dec(t.get("calendarWeeksHigh"))) + " calendar weeks for "
                + Md.plural(t.get("developers").asInt(), "developer", "developers") + ".";
    }

    private static String decisionLabel(String d) {
        return switch (d) {
            case "MIGRATE_FIRST" -> "Migrate first";
            case "MIGRATE_LATER" -> "Migrate later";
            default -> "Leave alone";
        };
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
