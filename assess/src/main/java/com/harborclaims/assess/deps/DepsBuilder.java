package com.harborclaims.assess.deps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.harborclaims.assess.common.Hashing;
import com.harborclaims.assess.common.Json;
import com.harborclaims.assess.common.PomReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds deps.json: the WAR's libraries, their advisories (OSV), and EOL flags. */
public final class DepsBuilder {

    public enum Mode {
        /** Cache only. A cache miss is reported as UNKNOWN and the network is never touched. */
        OFFLINE,
        /** Use the cache, query OSV for misses. */
        AUTO,
        /** Query OSV for everything and rewrite the cache. */
        REFRESH
    }

    public record Library(String file, String groupId, String artifactId, String version, String identifiedBy,
            String sha1, String osvStatus, List<String> advisoryIds, EolTable.Status eol, String duplicateOf) {
    }

    public record Advisory(String id, List<String> aliases, String summary, String severity, Double cvssScore,
            String severitySource, List<String> packages) {
    }

    private record Raw(String canonical, String osvId, List<String> aliases, String summary, String severity,
            Double cvss, String source, boolean withdrawn) {
    }

    private record Lookup(String status, JsonNode vulns, String fetchedAt) {
    }

    private static final Pattern CVE = Pattern.compile("CVE-(\\d{4})-(\\d+)");

    private final OsvCache cache;
    private final OsvClient client;
    private final Mode mode;
    private final Supplier<String> today;

    public DepsBuilder(OsvCache cache, OsvClient client, Mode mode, Supplier<String> today) {
        this.cache = cache;
        this.client = client;
        this.mode = mode;
        this.today = today;
    }

    public static Supplier<String> realToday() {
        return () -> LocalDate.now().toString();
    }

    public Map<String, Object> build(Path war, Path pom, Path referenceDir) throws IOException {
        EolTable eol = EolTable.load(referenceDir.resolve("eol.yaml"));
        Map<String, String> table = jarTable(referenceDir.resolve("jar-coordinates.yaml"));
        List<JarScanner.Jar> jars = JarScanner.scanWar(war);

        List<String> fingerprint = new ArrayList<>();
        fingerprint.add("eol.yaml:" + Hashing.sha256(referenceDir.resolve("eol.yaml")));
        fingerprint.add("jar-coordinates.yaml:" + Hashing.sha256(referenceDir.resolve("jar-coordinates.yaml")));

        Map<String, Raw> advisoryRaw = new TreeMap<>();
        Map<String, Set<String>> advisoryPackages = new TreeMap<>();
        Set<String> dataDates = new TreeSet<>();
        List<Library> libraries = new ArrayList<>();
        List<String> unidentified = new ArrayList<>();
        List<String> withdrawn = new TreeList();
        Map<String, String> seenSha = new LinkedHashMap<>();
        int unknownLookups = 0;
        int noRecords = 0;

        for (JarScanner.Jar jar : jars) {
            fingerprint.add("jar:" + jar.file() + ":" + jar.sha1());
            if (seenSha.containsKey(jar.sha1())) {
                libraries.add(new Library(jar.file(), null, null, null, null, jar.sha1(), "DUPLICATE", List.of(), null,
                        seenSha.get(jar.sha1())));
                continue;
            }
            seenSha.put(jar.sha1(), jar.file());
            JarScanner.Coordinates c = jar.primary();
            String identifiedBy = c != null ? "pom.properties" : null;
            String g = c == null ? null : c.groupId();
            String a = c == null ? null : c.artifactId();
            String v = c == null ? null : c.version();
            if (c == null && table.containsKey(jar.file())) {
                String[] parts = table.get(jar.file()).split(":");
                g = parts[0];
                a = parts[1];
                v = parts[2];
                identifiedBy = "reference-table";
            }
            if (g == null) {
                unidentified.add(jar.file());
                libraries.add(new Library(jar.file(), null, null, null, null, jar.sha1(), "UNIDENTIFIED", List.of(), null, null));
                continue;
            }
            Lookup l = lookup(g, a, v);
            if (l.status().equals("UNKNOWN_OFFLINE_MISS")) {
                unknownLookups++;
            } else {
                dataDates.add(l.fetchedAt());
                fingerprint.add("osv:" + g + ":" + a + ":" + v + ":" + Hashing.sha256(Json.toString(l.vulns()).getBytes(StandardCharsets.UTF_8)));
            }
            List<String> ids = collect(l, g + ":" + a + ":" + v, advisoryRaw, advisoryPackages, withdrawn);
            if (l.status().equals("NO_RECORDS")) {
                noRecords++;
            }
            libraries.add(new Library(jar.file(), g, a, v, identifiedBy, jar.sha1(), l.status(), ids,
                    eol.match(g, a, v), null));
        }

        // provided-scope dependencies are not in the WAR but run in Tomcat's lib
        List<Library> provided = new ArrayList<>();
        for (PomReader.Dependency d : PomReader.dependencies(pom)) {
            if (!d.scope().equals("provided")) {
                continue;
            }
            fingerprint.add("provided:" + d.coordinates() + ":" + d.version());
            Lookup l = lookup(d.groupId(), d.artifactId(), d.version());
            if (l.status().equals("UNKNOWN_OFFLINE_MISS")) {
                unknownLookups++;
            } else {
                dataDates.add(l.fetchedAt());
                fingerprint.add("osv:" + d.coordinates() + ":" + d.version() + ":" + Hashing.sha256(Json.toString(l.vulns()).getBytes(StandardCharsets.UTF_8)));
            }
            List<String> ids = collect(l, d.coordinates() + ":" + d.version(), advisoryRaw, advisoryPackages, withdrawn);
            provided.add(new Library(null, d.groupId(), d.artifactId(), d.version(), "pom (provided scope)", null,
                    l.status(), ids, eol.match(d.groupId(), d.artifactId(), d.version()), null));
        }
        provided.sort(Comparator.comparing(Library::artifactId));

        List<Advisory> advisories = new ArrayList<>();
        for (Map.Entry<String, Raw> e : advisoryRaw.entrySet()) {
            Raw r = e.getValue();
            advisories.add(new Advisory(r.canonical(), r.aliases(), r.summary(), r.severity(), r.cvss(), r.source(),
                    new ArrayList<>(advisoryPackages.get(e.getKey()))));
        }

        Map<String, Integer> bySeverity = new TreeMap<>();
        for (String s : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW", "NONE", "UNKNOWN")) {
            bySeverity.put(s, 0);
        }
        advisories.forEach(a -> bySeverity.merge(a.severity(), 1, Integer::sum));

        List<Library> inWar = libraries.stream().filter(l -> l.groupId() != null).toList();
        Map<String, Object> summary = new TreeMap<>();
        summary.put("jarsInWar", libraries.size());
        summary.put("duplicateJars", libraries.stream().filter(l -> l.duplicateOf() != null).count());
        summary.put("identifiedLibraries", inWar.size());
        summary.put("unidentifiedJars", unidentified.size());
        summary.put("librariesWithAdvisories", inWar.stream().filter(l -> !l.advisoryIds().isEmpty()).count());
        summary.put("librariesWithNoOsvRecords", noRecords);
        summary.put("findings", inWar.stream().mapToInt(l -> l.advisoryIds().size()).sum());
        summary.put("uniqueAdvisories", advisories.size());
        summary.put("uniqueAdvisoriesInWar", inWar.stream().flatMap(l -> l.advisoryIds().stream()).distinct().count());
        summary.put("uniqueAdvisoriesBySeverity", bySeverity);
        summary.put("eolFlaggedLibraries", inWar.stream().filter(l -> l.eol() != null).count());
        summary.put("lookupsUnknownOffline", unknownLookups);
        summary.put("withdrawnAdvisories", withdrawn.size());
        summary.put("providedFindings", provided.stream().mapToInt(l -> l.advisoryIds().size()).sum());

        Map<String, Object> provenance = new TreeMap<>();
        provenance.put("tool", "assess 1.0.0");
        provenance.put("dataSource", OsvClient.API);
        provenance.put("dataAsOf", dataDates.isEmpty() ? null : ((TreeSet<String>) dataDates).last());
        provenance.put("eolTableAsOf", eol.asOf());
        provenance.put("note", "Severity is the CVSS v3 base score computed from the OSV vector; if only a GHSA label exists it is used; otherwise UNKNOWN.");

        Map<String, Object> warInfo = new TreeMap<>();
        warInfo.put("file", war.getFileName().toString());
        warInfo.put("jarsFingerprint", Hashing.fingerprint(jars.stream().map(j -> j.file() + ":" + j.sha1()).toList()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schema", 1);
        out.put("provenance", provenance);
        out.put("inputsFingerprint", Hashing.fingerprint(fingerprint));
        out.put("war", warInfo);
        out.put("summary", summary);
        out.put("libraries", libraries);
        out.put("advisories", advisories);
        out.put("provided", provided);
        out.put("unidentified", unidentified);
        out.put("withdrawn", withdrawn);
        return out;
    }

    private static final class TreeList extends ArrayList<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean add(String s) {
            if (contains(s)) {
                return false;
            }
            super.add(s);
            sort(String::compareTo);
            return true;
        }
    }

    private Map<String, String> jarTable(Path yaml) throws IOException {
        JsonNode root = new ObjectMapper(new YAMLFactory()).readTree(Files.readAllBytes(yaml));
        Map<String, String> m = new TreeMap<>();
        root.path("jars").fields().forEachRemaining(e -> m.put(e.getKey(), e.getValue().asText()));
        return m;
    }

    private Lookup lookup(String g, String a, String v) throws IOException {
        if (mode != Mode.REFRESH) {
            Optional<OsvCache.Entry> hit = cache.read(g, a, v);
            if (hit.isPresent()) {
                return new Lookup(hit.get().vulns().isEmpty() ? "NO_RECORDS" : "OK", hit.get().vulns(), hit.get().fetchedAt());
            }
            if (mode == Mode.OFFLINE) {
                return new Lookup("UNKNOWN_OFFLINE_MISS", Json.MAPPER.createArrayNode(), null);
            }
        }
        ArrayNode vulns = client.query(g, a, v);
        String fetchedAt = today.get();
        cache.write(g, a, v, fetchedAt, vulns);
        return new Lookup(vulns.isEmpty() ? "NO_RECORDS" : "OK", vulns, fetchedAt);
    }

    /** Folds one coordinate's records into the global advisory map and returns this library's canonical ids. */
    private List<String> collect(Lookup l, String pkg, Map<String, Raw> advisoryRaw, Map<String, Set<String>> advisoryPackages,
            List<String> withdrawn) {
        Set<String> ids = new TreeSet<>();
        for (JsonNode v : l.vulns()) {
            Raw r = fromVuln(v);
            if (r.withdrawn()) {
                ((TreeList) withdrawn).add(r.canonical() + " (" + r.osvId() + ")");
                continue;
            }
            ids.add(r.canonical());
            Raw existing = advisoryRaw.get(r.canonical());
            if (existing == null) {
                advisoryRaw.put(r.canonical(), r);
            } else {
                Set<String> merged = new TreeSet<>(existing.aliases());
                merged.addAll(r.aliases());
                advisoryRaw.put(r.canonical(), new Raw(existing.canonical(), existing.osvId(), new ArrayList<>(merged),
                        existing.summary(), existing.severity(), existing.cvss(), existing.source(), false));
            }
            advisoryPackages.computeIfAbsent(r.canonical(), k -> new TreeSet<>()).add(pkg);
        }
        return new ArrayList<>(ids);
    }

    static Raw fromVuln(JsonNode v) {
        String id = v.path("id").asText();
        Set<String> all = new TreeSet<>();
        all.add(id);
        v.path("aliases").forEach(x -> all.add(x.asText()));
        String canonical = canonicalId(all, id);

        Double best = null;
        for (JsonNode s : v.path("severity")) {
            if (s.path("type").asText().equals("CVSS_V3")) {
                Double score = Cvss.baseScore(s.path("score").asText());
                if (score != null && (best == null || score > best)) {
                    best = score;
                }
            }
        }
        String severity;
        String source;
        if (best != null) {
            severity = Cvss.label(best);
            source = "cvss-v3-computed";
        } else {
            String ghsa = v.path("database_specific").path("severity").asText("").toUpperCase(java.util.Locale.ROOT);
            if (ghsa.equals("MODERATE")) {
                ghsa = "MEDIUM";
            }
            if (List.of("CRITICAL", "HIGH", "MEDIUM", "LOW").contains(ghsa)) {
                severity = ghsa;
                source = "ghsa-label";
            } else {
                severity = "UNKNOWN";
                source = "none";
            }
        }
        List<String> aliases = new ArrayList<>(all);
        aliases.remove(canonical);
        return new Raw(canonical, id, aliases, v.path("summary").asText(""), severity, best, source,
                v.hasNonNull("withdrawn"));
    }

    /** The lowest CVE id (by year, then number); with no CVE, the GHSA id if present, otherwise the record id. */
    static String canonicalId(Set<String> ids, String fallback) {
        String best = null;
        long bestKey = Long.MAX_VALUE;
        for (String s : ids) {
            Matcher m = CVE.matcher(s);
            if (m.matches()) {
                long key = Long.parseLong(m.group(1)) * 10_000_000L + Long.parseLong(m.group(2));
                if (key < bestKey) {
                    bestKey = key;
                    best = s;
                }
            }
        }
        if (best != null) {
            return best;
        }
        for (String s : ids) {
            if (s.startsWith("GHSA-")) {
                return s;
            }
        }
        return fallback;
    }
}
