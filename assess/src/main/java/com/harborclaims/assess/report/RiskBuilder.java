package com.harborclaims.assess.report;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds the risk register: one risk per root cause (three SQL injection sites are one risk), dependencies grouped by
 * library family, EOL and CVEs on the same family merged into one risk. Order is deterministic.
 */
public final class RiskBuilder {

    public record Risk(String id, String group, String title, String evidence, int likelihood, int impact, int score,
            boolean assumed) {
    }

    public record Selection(List<Risk> top, List<String> omittedByCap, int moreAtCutoffScore) {
    }

    private RiskBuilder() {
    }

    public static List<Risk> register(RiskRules rules, JsonNode security, JsonNode deps, JsonNode structure, JsonNode jakarta) {
        List<Risk> out = new ArrayList<>();

        // ---- application security: one risk per category
        for (JsonNode f : security.get("findings")) {
            String cat = f.get("category").asText();
            RiskRules.Cell cell = rules.securityCategories().get(cat);
            if (cell == null) {
                throw new IllegalArgumentException("risk rules: securityCategories." + cat + " is missing");
            }
            String evidence = Md.unit(f.get("count").asInt(), f.get("countUnit").asText());
            out.add(risk("SEC-" + cat, "Application security", f.get("title").asText() + " (" + f.get("cwe").asText() + ")",
                    evidence, cell.likelihood(), cell.impact(), false));
        }

        // ---- dependencies: one risk per library family
        Map<String, String> advisorySeverity = new TreeMap<>();
        deps.get("advisories").forEach(a -> advisorySeverity.put(a.get("id").asText(), a.get("severity").asText()));
        for (String section : new String[] {"libraries", "provided"}) {
            Map<String, List<JsonNode>> families = new TreeMap<>();
            for (JsonNode l : deps.get(section)) {
                if (!l.get("groupId").isNull()) {
                    families.computeIfAbsent(l.get("groupId").asText(), k -> new ArrayList<>()).add(l);
                }
            }
            for (Map.Entry<String, List<JsonNode>> e : families.entrySet()) {
                Risk r = dependencyRisk(rules, e.getKey(), e.getValue(), advisorySeverity, section.equals("provided"));
                if (r != null) {
                    out.add(r);
                }
            }
        }

        // ---- structure
        List<String> gods = new ArrayList<>();
        for (JsonNode c : structure.get("largestClasses")) {
            if (c.get("godClass").asBoolean()) {
                gods.add(Md.shortName(c.get("className").asText()) + " (" + c.get("lines").asInt() + " lines)");
            }
        }
        if (!gods.isEmpty()) {
            RiskRules.Level l = rules.structure().get("GOD_CLASS");
            out.add(risk("STR-GOD-CLASS", "Structure", "God class", String.join(", ", gods), l.likelihood(), l.impact(), false));
        }
        JsonNode cycles = structure.get("packageCycles");
        if (cycles.size() > 0) {
            RiskRules.Level l = rules.structure().get("PACKAGE_CYCLES");
            List<String> members = new ArrayList<>();
            cycles.forEach(c -> {
                List<String> names = new ArrayList<>();
                c.get("members").forEach(m -> names.add(Md.shortName(m.asText())));
                members.add(String.join(" + ", names));
            });
            out.add(risk("STR-PACKAGE-CYCLES", "Structure", "Package dependency cycles",
                    Md.plural(cycles.size(), "cycle", "cycles") + ": " + String.join("; ", members), l.likelihood(), l.impact(), false));
        }

        // ---- platform: Struts 1 has no Jakarta build
        boolean struts1Eol = false;
        for (JsonNode l : deps.get("libraries")) {
            if (!l.get("groupId").isNull() && l.get("groupId").asText().equals("org.apache.struts") && !l.get("eol").isNull()
                    && l.get("eol").get("status").asText().equals("EOL")) {
                struts1Eol = true;
            }
        }
        if (struts1Eol) {
            RiskRules.Level l = rules.platform().get("NO_JAKARTA_STRUTS");
            JsonNode main = jakarta.get("javaMain");
            out.add(risk("PLT-NO-JAKARTA-STRUTS", "Platform", "Struts 1 has no Jakarta build, so it blocks a javax to jakarta upgrade",
                    main.get("jakartaMigratingImports").asInt() + " javax.servlet imports in " + main.get("filesWithJakartaMigratingJavax").asInt()
                            + " files", l.likelihood(), l.impact(), false));
        }

        // ---- knowledge risks (hand-set inputs, labelled as assumptions)
        for (RiskRules.Knowledge k : rules.knowledge()) {
            out.add(risk(k.id(), "Knowledge", k.title(), k.assumed() ? "assumption, confirm with the client" : "", k.likelihood(),
                    k.impact(), k.assumed()));
        }

        List<String> groupOrder = rules.groupOrder();
        out.sort(Comparator.comparingInt(Risk::score).reversed().thenComparing(Comparator.comparingInt(Risk::impact).reversed())
                .thenComparingInt(r -> groupOrder.indexOf(r.group())).thenComparing(Risk::id));
        return out;
    }

    private static Risk dependencyRisk(RiskRules rules, String group, List<JsonNode> libs, Map<String, String> severityOf,
            boolean provided) {
        TreeSet<String> advisories = new TreeSet<>();
        TreeSet<String> artifacts = new TreeSet<>();
        TreeSet<String> versions = new TreeSet<>();
        String eolStatus = null;
        String eolSince = null;
        for (JsonNode l : libs) {
            l.get("advisoryIds").forEach(a -> advisories.add(a.asText()));
            artifacts.add(l.get("artifactId").asText());
            versions.add(l.get("version").asText());
            if (!l.get("eol").isNull() && eolStatus == null) {
                eolStatus = l.get("eol").get("status").asText();
                eolSince = l.get("eol").get("since").isNull() ? null : l.get("eol").get("since").asText();
            }
        }
        if (advisories.isEmpty() && eolStatus == null) {
            return null;
        }
        int bestL = 0;
        int bestI = 0;
        Map<String, Integer> bySeverity = new TreeMap<>();
        for (String id : advisories) {
            String sev = severityOf.getOrDefault(id, "UNKNOWN");
            bySeverity.merge(sev, 1, Integer::sum);
            RiskRules.Level lv = rules.dependencySeverity().get(sev);
            if (lv.likelihood() * lv.impact() > bestL * bestI
                    || (lv.likelihood() * lv.impact() == bestL * bestI && lv.impact() > bestI)) {
                bestL = lv.likelihood();
                bestI = lv.impact();
            }
        }
        if (eolStatus != null) {
            RiskRules.Level lv = rules.eolStatus().get(eolStatus);
            if (lv.likelihood() * lv.impact() > bestL * bestI
                    || (lv.likelihood() * lv.impact() == bestL * bestI && lv.impact() > bestI)) {
                bestL = lv.likelihood();
                bestI = lv.impact();
            }
        }
        List<String> parts = new ArrayList<>();
        if (!advisories.isEmpty()) {
            List<String> sev = new ArrayList<>();
            for (String s : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN")) {
                if (bySeverity.containsKey(s)) {
                    sev.add(bySeverity.get(s) + " " + Md.lower(s));
                }
            }
            parts.add(Md.plural(advisories.size(), "advisory", "advisories") + " (" + String.join(", ", sev) + ")");
        }
        if (eolStatus != null) {
            parts.add(Md.lower(eolStatus).replace("eol", "end of life") + (eolSince == null ? "" : " since " + eolSince));
        }
        String title = String.join(", ", artifacts) + " " + String.join("/", versions)
                + (provided ? " (provided by the container, not in the WAR)" : "");
        return risk("DEP-" + (provided ? "PROVIDED-" : "") + group, "Dependencies", title, String.join("; ", parts), bestL, bestI, false);
    }

    private static Risk risk(String id, String group, String title, String evidence, int likelihood, int impact, boolean assumed) {
        return new Risk(id, group, title, evidence, likelihood, impact, likelihood * impact, assumed);
    }

    /** Takes the register in order, at most perGroupCap per group, up to topN. */
    public static Selection top(List<Risk> register, RiskRules rules) {
        List<Risk> top = new ArrayList<>();
        List<String> omitted = new ArrayList<>();
        Map<String, Integer> perGroup = new LinkedHashMap<>();
        List<Risk> notTaken = new ArrayList<>();
        for (Risk r : register) {
            if (top.size() >= rules.topN()) {
                notTaken.add(r);
                continue;
            }
            int used = perGroup.getOrDefault(r.group(), 0);
            if (used >= rules.perGroupCap()) {
                omitted.add(r.id());
                continue;
            }
            perGroup.put(r.group(), used + 1);
            top.add(r);
        }
        int cutoff = top.isEmpty() ? 0 : top.get(top.size() - 1).score();
        int tied = (int) notTaken.stream().filter(r -> r.score() == cutoff).count();
        return new Selection(top, omitted, tied);
    }
}
