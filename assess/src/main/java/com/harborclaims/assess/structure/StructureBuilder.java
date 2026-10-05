package com.harborclaims.assess.structure;

import com.fasterxml.jackson.databind.JsonNode;
import com.harborclaims.assess.common.Hashing;
import com.harborclaims.assess.common.Modules;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Builds structure.json: package and module dependency graph, cycles, fan-in/out and the largest classes. */
public final class StructureBuilder {

    public static final int GOD_CLASS_LINES = 1000;
    public static final int TOP_CLASSES = 10;

    public record PackageItem(String name, String module, int classes, int lines, int fanOut, int fanIn, int instabilityPercent) {
    }

    public record ModuleItem(String name, int packages, int classes, int lines, int fanOut, int fanIn,
            int instabilityPercent, int classEdgesOut, int classEdgesIn, int classEdgesOutExcludingPlatform,
            int classEdgesInExcludingPlatform) {
    }

    public record Edge(String from, String to, int classEdges) {
    }

    public record Cycle(List<String> members, List<String> path, List<String> witnesses) {
    }

    public record ClassItem(String className, String file, String module, int lines, boolean godClass) {
    }

    private StructureBuilder() {
    }

    public static Map<String, Object> build(JsonNode inventory, Set<String> classEdges, Set<String> sourcePackageEdges,
            Modules modules, String modulesYamlSha256) {
        // ---- classes and packages from the inventory
        Map<String, Integer> pkgClasses = new TreeMap<>();
        Map<String, Integer> pkgLines = new TreeMap<>();
        List<ClassItem> classes = new ArrayList<>();
        for (JsonNode j : inventory.get("javaMain")) {
            String pkg = j.get("pkg").asText();
            int lines = j.get("lines").asInt();
            pkgClasses.merge(pkg, 1, Integer::sum);
            pkgLines.merge(pkg, lines, Integer::sum);
            classes.add(new ClassItem(pkg + "." + j.get("className").asText(), j.get("file").asText(),
                    modules.forPackage(pkg), lines, lines >= GOD_CLASS_LINES));
        }

        // ---- package edges aggregated from class edges, and module edges
        Map<String, Integer> pkgEdgeCount = new TreeMap<>();
        Map<String, Integer> moduleEdgeCount = new TreeMap<>();
        for (String e : classEdges) {
            String[] parts = e.split(" -> ");
            String fromPkg = JdepsRunner.packageOf(parts[0]);
            String toPkg = JdepsRunner.packageOf(parts[1]);
            if (!fromPkg.equals(toPkg)) {
                pkgEdgeCount.merge(fromPkg + " -> " + toPkg, 1, Integer::sum);
            }
            String fromMod = modules.forPackage(fromPkg);
            String toMod = modules.forPackage(toPkg);
            if (!fromMod.equals(toMod)) {
                moduleEdgeCount.merge(fromMod + " -> " + toMod, 1, Integer::sum);
            }
        }
        List<Edge> pkgEdges = edges(pkgEdgeCount);
        List<Edge> moduleEdges = edges(moduleEdgeCount);

        // ---- package items
        Map<String, Set<String>> pkgOut = new TreeMap<>();
        Map<String, Set<String>> pkgIn = new TreeMap<>();
        for (String p : pkgClasses.keySet()) {
            pkgOut.put(p, new TreeSet<>());
            pkgIn.put(p, new TreeSet<>());
        }
        for (Edge e : pkgEdges) {
            pkgOut.computeIfAbsent(e.from(), k -> new TreeSet<>()).add(e.to());
            pkgIn.computeIfAbsent(e.to(), k -> new TreeSet<>()).add(e.from());
        }
        List<PackageItem> packages = new ArrayList<>();
        for (String p : pkgClasses.keySet()) {
            int out = pkgOut.get(p).size();
            int in = pkgIn.getOrDefault(p, Set.of()).size();
            packages.add(new PackageItem(p, modules.forPackage(p), pkgClasses.get(p), pkgLines.get(p), out, in, instability(out, in)));
        }

        // ---- module items
        List<ModuleItem> moduleItems = new ArrayList<>();
        for (String m : modules.names()) {
            Set<String> out = new TreeSet<>();
            Set<String> in = new TreeSet<>();
            int edgesOut = 0;
            int edgesIn = 0;
            int edgesOutNoPlatform = 0;
            int edgesInNoPlatform = 0;
            for (Edge e : moduleEdges) {
                if (e.from().equals(m)) {
                    out.add(e.to());
                    edgesOut += e.classEdges();
                    if (!e.to().equals("Platform")) {
                        edgesOutNoPlatform += e.classEdges();
                    }
                }
                if (e.to().equals(m)) {
                    in.add(e.from());
                    edgesIn += e.classEdges();
                    if (!e.from().equals("Platform")) {
                        edgesInNoPlatform += e.classEdges();
                    }
                }
            }
            List<PackageItem> mp = packages.stream().filter(p -> p.module().equals(m)).toList();
            moduleItems.add(new ModuleItem(m, mp.size(), mp.stream().mapToInt(PackageItem::classes).sum(),
                    mp.stream().mapToInt(PackageItem::lines).sum(), out.size(), in.size(), instability(out.size(), in.size()),
                    edgesOut, edgesIn, edgesOutNoPlatform, edgesInNoPlatform));
        }

        // ---- cycles
        Map<String, Set<String>> pkgAdj = Graphs.adjacency(pkgClasses.keySet(), pkgEdges.stream().map(e -> new String[] {e.from(), e.to()}).toList());
        List<Cycle> pkgCycles = cycles(pkgAdj, classEdges, true);
        Map<String, Set<String>> modAdj = Graphs.adjacency(modules.names(), moduleEdges.stream().map(e -> new String[] {e.from(), e.to()}).toList());
        List<Cycle> moduleCycles = cycles(modAdj, classEdges, false);

        // ---- cross-check against imports
        Set<String> jdepsPkgEdges = new TreeSet<>();
        pkgEdges.forEach(e -> jdepsPkgEdges.add(e.from() + " -> " + e.to()));
        Set<String> onlyJdeps = new TreeSet<>(jdepsPkgEdges);
        onlyJdeps.removeAll(sourcePackageEdges);
        Set<String> onlySource = new TreeSet<>(sourcePackageEdges);
        onlySource.removeAll(jdepsPkgEdges);

        // ---- largest classes
        List<ClassItem> largest = new ArrayList<>(classes);
        largest.sort(Comparator.comparingInt(ClassItem::lines).reversed().thenComparing(ClassItem::className));
        List<ClassItem> top = largest.subList(0, Math.min(TOP_CLASSES, largest.size()));

        Map<String, Object> summary = new TreeMap<>();
        summary.put("packages", packages.size());
        summary.put("classes", classes.size());
        summary.put("classEdges", classEdges.size());
        summary.put("packageEdges", pkgEdges.size());
        summary.put("moduleEdges", moduleEdges.size());
        summary.put("packageCycles", pkgCycles.size());
        summary.put("packagesInCycles", pkgCycles.stream().mapToInt(c -> c.members().size()).sum());
        summary.put("moduleCycles", moduleCycles.size());
        summary.put("godClasses", classes.stream().filter(ClassItem::godClass).count());
        summary.put("largestClassLines", top.isEmpty() ? 0 : top.get(0).lines());
        summary.put("importGraphAgreesWithJdeps", onlySource.isEmpty());

        Map<String, Object> parameters = new TreeMap<>();
        parameters.put("godClassThresholdLines", GOD_CLASS_LINES);
        parameters.put("graphSource", "jdeps -verbose:class on legacy-app/target/classes, folded to top-level classes");
        parameters.put("instabilityPercent", "fanOut * 100 / (fanIn + fanOut), rounded half up (Martin's I)");

        List<String> fp = new ArrayList<>();
        fp.add("inventory:" + inventory.get("inputsFingerprint").asText());
        fp.add("modules.yaml:" + modulesYamlSha256);
        classEdges.forEach(e -> fp.add("edge:" + e));
        sourcePackageEdges.forEach(e -> fp.add("import:" + e));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schema", 1);
        out.put("tool", "assess 1.0.0");
        out.put("source", "legacy-app");
        out.put("inputsFingerprint", Hashing.sha256(String.join("\n", fp.stream().sorted().toList()).getBytes(StandardCharsets.UTF_8)));
        out.put("parameters", parameters);
        out.put("summary", summary);
        out.put("modules", moduleItems);
        out.put("packages", packages);
        out.put("moduleEdges", moduleEdges);
        out.put("packageEdges", pkgEdges);
        out.put("packageCycles", pkgCycles);
        out.put("moduleCycles", moduleCycles);
        out.put("largestClasses", top);
        Map<String, Object> cross = new TreeMap<>();
        cross.put("packageEdgesOnlyInJdeps", onlyJdeps);
        cross.put("packageEdgesOnlyInImports", onlySource);
        out.put("crossCheck", cross);
        return out;
    }

    private static List<Edge> edges(Map<String, Integer> counts) {
        List<Edge> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String[] p = e.getKey().split(" -> ");
            out.add(new Edge(p[0], p[1], e.getValue()));
        }
        return out;
    }

    /** Witnesses are the class-to-class edges that make up the cycle (package cycles only). */
    private static List<Cycle> cycles(Map<String, Set<String>> adj, Set<String> classEdges, boolean withWitnesses) {
        List<Cycle> out = new ArrayList<>();
        for (List<String> scc : Graphs.cycles(adj)) {
            List<String> witnesses = new ArrayList<>();
            if (withWitnesses) {
                for (String e : classEdges) {
                    String[] p = e.split(" -> ");
                    String fp = JdepsRunner.packageOf(p[0]);
                    String tp = JdepsRunner.packageOf(p[1]);
                    if (!fp.equals(tp) && scc.contains(fp) && scc.contains(tp)) {
                        witnesses.add(e);
                    }
                }
            }
            out.add(new Cycle(scc, Graphs.cyclePath(adj, scc), witnesses));
        }
        return out;
    }

    static int instability(int fanOut, int fanIn) {
        int total = fanOut + fanIn;
        return total == 0 ? 0 : (fanOut * 100 + total / 2) / total;
    }
}
