package com.harborclaims.assess.estimate;

import com.fasterxml.jackson.databind.JsonNode;
import com.harborclaims.assess.common.Modules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Everything the estimate needs, derived from the results files. No number in here is typed by hand. */
public record Facts(List<ModuleFact> modules, List<CycleEdge> cycleEdges, Map<String, Integer> securitySitesByCategory,
        List<String> libraryFamilies) {

    public record ActionFact(String path, String type, String kind, boolean inScope, List<String> jspFiles) {
    }

    public record JspFact(String file, int blocks) {
    }

    /** A package dependency inside a cycle, with the modules of both ends (resolved through modules.yaml). */
    public record CycleEdge(String from, String to, String fromModule, String toModule) {
    }

    public record ModuleFact(String name, List<ActionFact> actions, List<JspFact> jsps, int daos, int managers,
            int batchJobs, int pdfReports, int classes, int couplingOut, int couplingIn, boolean inCycle, int securitySites,
            List<String> godClasses) {
    }

    public static final String PLATFORM = "Platform";

    public ModuleFact module(String name) {
        return modules.stream().filter(m -> m.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no such module: " + name));
    }

    public static Facts build(JsonNode inventory, JsonNode structure, JsonNode security, JsonNode deps, Modules modules,
            Model model) {
        // tile name -> body jsp (without the leading slash), so forwards, inputs and ForwardAction parameters resolve the same way
        Map<String, String> tileBody = new TreeMap<>();
        for (JsonNode t : inventory.get("tilesDefinitions")) {
            if (!t.get("resolvedBody").isNull()) {
                tileBody.put(t.get("name").asText(), strip(t.get("resolvedBody").asText()));
            }
        }

        // a custom action class must belong to exactly one module
        Map<String, String> classOwner = new TreeMap<>();
        Map<String, List<ActionFact>> actionsByModule = new LinkedHashMap<>();
        for (String m : modules.names()) {
            actionsByModule.put(m, new ArrayList<>());
        }
        for (JsonNode a : inventory.get("actions")) {
            String module = a.get("module").asText();
            String type = a.get("type").asText();
            if (!type.startsWith("org.apache.struts.")) {
                String owner = classOwner.putIfAbsent(type, module);
                if (owner != null && !owner.equals(module)) {
                    throw new IllegalStateException("Action class " + type + " is mapped in two modules: " + owner + " and " + module);
                }
            }
            TreeSet<String> jsps = new TreeSet<>();
            for (JsonNode f : a.get("forwards")) {
                String kind = f.get("kind").asText();
                if (kind.equals("tile") && tileBody.containsKey(f.get("path").asText())) {
                    jsps.add(tileBody.get(f.get("path").asText()));
                } else if (kind.equals("jsp")) {
                    jsps.add(strip(f.get("path").asText()));
                }
            }
            for (String field : new String[] {"input", "parameter"}) {
                if (!a.get(field).isNull() && tileBody.containsKey(a.get(field).asText())) {
                    jsps.add(tileBody.get(a.get(field).asText()));
                }
            }
            actionsByModule.get(module).add(new ActionFact(a.get("path").asText(), type, a.get("kind").asText(),
                    a.get("migrationScope").asBoolean(), new ArrayList<>(jsps)));
        }

        // modules in packages that sit in a cycle (cycles made only of Platform packages do not count)
        List<CycleEdge> cycleEdges = new ArrayList<>();
        TreeSet<String> cycleModules = new TreeSet<>();
        for (JsonNode c : structure.get("packageCycles")) {
            TreeSet<String> members = new TreeSet<>();
            c.get("members").forEach(x -> members.add(x.asText()));
            boolean onlyPlatform = members.stream().allMatch(p -> modules.forPackage(p).equals(PLATFORM));
            if (onlyPlatform) {
                continue;
            }
            members.forEach(p -> cycleModules.add(modules.forPackage(p)));
            TreeSet<String> seen = new TreeSet<>();
            for (JsonNode w : c.get("witnesses")) {
                String[] parts = w.asText().split(" -> ");
                String fp = parts[0].substring(0, parts[0].lastIndexOf('.'));
                String tp = parts[1].substring(0, parts[1].lastIndexOf('.'));
                if (members.contains(fp) && members.contains(tp) && seen.add(fp + " -> " + tp)) {
                    cycleEdges.add(new CycleEdge(fp, tp, modules.forPackage(fp), modules.forPackage(tp)));
                }
            }
        }

        // God classes by module
        Map<String, List<String>> gods = new TreeMap<>();
        for (JsonNode c : structure.get("largestClasses")) {
            if (c.get("godClass").asBoolean()) {
                gods.computeIfAbsent(c.get("module").asText(), k -> new ArrayList<>()).add(c.get("className").asText());
            }
        }

        // security sites by module
        Map<String, Integer> sitesByModule = new TreeMap<>();
        Map<String, Integer> sitesByCategory = new TreeMap<>();
        for (JsonNode f : security.get("findings")) {
            sitesByCategory.put(f.get("category").asText(), f.get("count").asInt());
            for (JsonNode l : f.get("locations")) {
                sitesByModule.merge(moduleOfLocation(l, modules), 1, Integer::sum);
            }
        }

        // JSPs, DAOs, managers, jobs and PDF builders per module
        Map<String, List<JspFact>> jspsByModule = new LinkedHashMap<>();
        modules.names().forEach(m -> jspsByModule.put(m, new ArrayList<>()));
        for (JsonNode j : inventory.get("jsps")) {
            jspsByModule.get(j.get("module").asText()).add(new JspFact(j.get("file").asText(),
                    j.get("scriptlets").asInt() + j.get("expressions").asInt() + j.get("declarations").asInt()));
        }
        Map<String, Integer> jobs = new TreeMap<>();
        Map<String, Integer> pdfs = new TreeMap<>();
        for (JsonNode j : inventory.get("javaMain")) {
            String cls = j.get("className").asText();
            if (cls.endsWith(model.detect().batchJobClassSuffix())) {
                jobs.merge(j.get("module").asText(), 1, Integer::sum);
            }
            if (cls.endsWith(model.detect().pdfReportClassSuffix())) {
                pdfs.merge(j.get("module").asText(), 1, Integer::sum);
            }
        }
        Map<String, JsonNode> inv = new TreeMap<>();
        inventory.get("modules").forEach(m -> inv.put(m.get("name").asText(), m));
        Map<String, JsonNode> str = new TreeMap<>();
        structure.get("modules").forEach(m -> str.put(m.get("name").asText(), m));

        List<ModuleFact> out = new ArrayList<>();
        for (String m : modules.names()) {
            JsonNode i = inv.get(m);
            JsonNode s = str.get(m);
            out.add(new ModuleFact(m, actionsByModule.get(m), jspsByModule.get(m), i.get("daos").asInt(), i.get("managers").asInt(),
                    jobs.getOrDefault(m, 0), pdfs.getOrDefault(m, 0), s.get("classes").asInt(),
                    s.get("classEdgesOutExcludingPlatform").asInt(), s.get("classEdgesInExcludingPlatform").asInt(),
                    cycleModules.contains(m), sitesByModule.getOrDefault(m, 0), gods.getOrDefault(m, List.of())));
        }

        // library families (by group id) that have advisories or are end of life, in the WAR or provided
        TreeSet<String> families = new TreeSet<>();
        for (String section : new String[] {"libraries", "provided"}) {
            for (JsonNode l : deps.get(section)) {
                if (!l.get("groupId").isNull() && (l.get("advisoryIds").size() > 0 || !l.get("eol").isNull())) {
                    families.add(l.get("groupId").asText());
                }
            }
        }
        return new Facts(out, cycleEdges, sitesByCategory, new ArrayList<>(families));
    }

    static String moduleOfLocation(JsonNode loc, Modules modules) {
        if (!loc.get("path").isNull()) {
            return modules.forActionPath(loc.get("path").asText());
        }
        String file = loc.get("file").asText();
        if (file.endsWith(".jsp")) {
            return modules.forJsp(file.substring(file.lastIndexOf('/') + 1));
        }
        String pkg = file.substring(file.indexOf("java/") + 5, file.lastIndexOf('/')).replace('/', '.');
        return modules.forPackage(pkg);
    }

    private static String strip(String s) {
        return s.startsWith("/") ? s.substring(1) : s;
    }
}
