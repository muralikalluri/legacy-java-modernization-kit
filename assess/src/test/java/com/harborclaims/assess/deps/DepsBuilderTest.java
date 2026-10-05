package com.harborclaims.assess.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.harborclaims.assess.common.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DepsBuilderTest {

    private static final Path REFERENCE = Path.of("reference");
    private static final String V3_CRITICAL = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H";
    private static final String V4_ONLY = "CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N";

    @TempDir
    Path tmp;

    private Path war;
    private Path pom;
    private OsvCache cache;

    /** A fetcher that fails the test if anything touches the network. */
    private static final HttpFetcher NO_NETWORK = (url, body) -> {
        throw new AssertionError("the network must not be used");
    };

    @BeforeEach
    void setUp() throws Exception {
        cache = new OsvCache(tmp.resolve("cache"));
        byte[] struts = jar("org.apache.struts", "struts-core", "1.3.10");
        byte[] beanutils = jar("commons-beanutils", "commons-beanutils", "1.8.0");
        byte[] logging = jar(null, null, null);
        byte[] mystery = "not a known jar".getBytes(StandardCharsets.UTF_8);
        war = tmp.resolve("app.war");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(war))) {
            put(z, "WEB-INF/lib/struts-core-1.3.10.jar", struts);
            put(z, "WEB-INF/lib/commons-beanutils-1.8.0.jar", beanutils);
            put(z, "WEB-INF/lib/commons-logging-1.0.4.jar", logging);
            put(z, "WEB-INF/lib/commons-logging-1.0.5.jar", logging);
            put(z, "WEB-INF/lib/mystery-1.0.jar", mystery);
            put(z, "WEB-INF/web.xml", "<web-app/>".getBytes(StandardCharsets.UTF_8));
        }
        pom = tmp.resolve("pom.xml");
        Files.writeString(pom, "<project><dependencies>"
                + "<dependency><groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId><version>3.1.0</version><scope>provided</scope></dependency>"
                + "<dependency><groupId>org.apache.struts</groupId><artifactId>struts-core</artifactId><version>1.3.10</version></dependency>"
                + "</dependencies></project>");

        cache.write("org.apache.struts", "struts-core", "1.3.10", "2026-01-01", array(
                vuln("GHSA-aaaa-0000-0001", List.of("CVE-2016-1181"), V3_CRITICAL, null, false),
                vuln("GHSA-bbbb-0000-0002", List.of("CVE-2014-0114"), null, "MODERATE", false),
                vuln("GHSA-cccc-0000-0003", List.of(), V4_ONLY, null, false),
                vuln("GHSA-dddd-0000-0004", List.of("CVE-2010-0001"), V3_CRITICAL, null, true)));
        cache.write("commons-beanutils", "commons-beanutils", "1.8.0", "2026-02-01", array(
                vuln("GHSA-eeee-0000-0005", List.of("CVE-2014-0114", "CVE-2019-10086"), V3_CRITICAL, null, false)));
        cache.write("commons-logging", "commons-logging", "1.0.4", "2026-01-15", array());
        cache.write("javax.servlet", "javax.servlet-api", "3.1.0", "2026-01-20", array());
    }

    @Test
    void offlineWithAFullCacheNeverTouchesTheNetwork() throws Exception {
        Map<String, Object> out = builder(DepsBuilder.Mode.OFFLINE, NO_NETWORK).build(war, pom, REFERENCE);
        assertEquals(0, summary(out).get("lookupsUnknownOffline"));
    }

    @Test
    void offlineMarksACacheMissUnknownInsteadOfCallingOut() throws Exception {
        Files.delete(cache.fileFor("commons-beanutils", "commons-beanutils", "1.8.0"));
        Map<String, Object> out = builder(DepsBuilder.Mode.OFFLINE, NO_NETWORK).build(war, pom, REFERENCE);
        assertEquals(1, summary(out).get("lookupsUnknownOffline"));
        assertEquals("UNKNOWN_OFFLINE_MISS", byFile(libraries(out), "commons-beanutils-1.8.0.jar").osvStatus());
    }

    @Test
    void identificationUsesPomPropertiesThenTheReferenceTableAndNeverDropsAJar() throws Exception {
        Map<String, Object> out = builder(DepsBuilder.Mode.OFFLINE, NO_NETWORK).build(war, pom, REFERENCE);
        List<DepsBuilder.Library> libs = libraries(out);
        assertEquals(5, libs.size());
        assertEquals("pom.properties", byFile(libs, "struts-core-1.3.10.jar").identifiedBy());
        assertEquals("reference-table", byFile(libs, "commons-logging-1.0.4.jar").identifiedBy());
        assertEquals("UNIDENTIFIED", byFile(libs, "mystery-1.0.jar").osvStatus());
        assertEquals(List.of("mystery-1.0.jar"), out.get("unidentified"));
    }

    @Test
    void identicalJarsAreCountedOnce() throws Exception {
        Map<String, Object> out = builder(DepsBuilder.Mode.OFFLINE, NO_NETWORK).build(war, pom, REFERENCE);
        DepsBuilder.Library dup = byFile(libraries(out), "commons-logging-1.0.5.jar");
        assertEquals("DUPLICATE", dup.osvStatus());
        assertEquals("commons-logging-1.0.4.jar", dup.duplicateOf());
        assertEquals(1L, summary(out).get("duplicateJars"));
        assertEquals(3, summary(out).get("identifiedLibraries"));
    }

    @Test
    void aliasesAreMergedAndTheSameCveAcrossTwoPackagesIsOneAdvisory() throws Exception {
        Map<String, Object> out = builder(DepsBuilder.Mode.OFFLINE, NO_NETWORK).build(war, pom, REFERENCE);
        List<DepsBuilder.Advisory> adv = advisories(out);
        DepsBuilder.Advisory shared = adv.stream().filter(a -> a.id().equals("CVE-2014-0114")).findFirst().orElseThrow();
        assertEquals(List.of("commons-beanutils:commons-beanutils:1.8.0", "org.apache.struts:struts-core:1.3.10"),
                shared.packages(), "one advisory, two affected packages");
        assertTrue(shared.aliases().contains("GHSA-bbbb-0000-0002") || shared.aliases().contains("GHSA-eeee-0000-0005"));
        // 3 advisories on struts (withdrawn excluded) + 1 on beanutils (shared with struts) + 1 more alias
        assertEquals(2, advisories(out).stream().filter(a -> a.packages().size() >= 1 && a.id().startsWith("CVE-")).count());
    }

    @Test
    void findingsAndUniqueAdvisoriesAreReportedSeparately() throws Exception {
        Map<String, Object> out = builder(DepsBuilder.Mode.OFFLINE, NO_NETWORK).build(war, pom, REFERENCE);
        Map<String, Object> s = summary(out);
        // struts: CVE-2016-1181, CVE-2014-0114, GHSA-cccc = 3 findings; beanutils: CVE-2014-0114 = 1 finding
        assertEquals(4, s.get("findings"));
        assertEquals(3, s.get("uniqueAdvisories"));
    }

    @Test
    void withdrawnRecordsAreListedButNotCounted() throws Exception {
        Map<String, Object> out = builder(DepsBuilder.Mode.OFFLINE, NO_NETWORK).build(war, pom, REFERENCE);
        assertEquals(1, summary(out).get("withdrawnAdvisories"));
        assertTrue(((List<?>) out.get("withdrawn")).get(0).toString().contains("CVE-2010-0001"));
        assertTrue(advisories(out).stream().noneMatch(a -> a.id().equals("CVE-2010-0001")));
    }

    @Test
    void severityComesFromComputedCvssThenTheGhsaLabelThenUnknown() throws Exception {
        Map<String, Object> out = builder(DepsBuilder.Mode.OFFLINE, NO_NETWORK).build(war, pom, REFERENCE);
        DepsBuilder.Advisory computed = advisories(out).stream().filter(a -> a.id().equals("CVE-2016-1181")).findFirst().orElseThrow();
        assertEquals("CRITICAL", computed.severity());
        assertEquals(9.8, computed.cvssScore());
        assertEquals("cvss-v3-computed", computed.severitySource());

        // CVE-2014-0114 is CRITICAL through beanutils' vector (the first record kept for the id wins), so check
        // the label path with a dedicated record: GHSA-cccc has only a v4 vector and no label, so it is UNKNOWN.
        DepsBuilder.Advisory unknown = advisories(out).stream().filter(a -> a.id().equals("GHSA-cccc-0000-0003")).findFirst().orElseThrow();
        assertEquals("UNKNOWN", unknown.severity(), "a v4-only record must be UNKNOWN, never 0 or LOW");
        assertNull(unknown.cvssScore());
        assertEquals("none", unknown.severitySource());
    }

    @Test
    void ghsaLabelIsUsedWhenThereIsNoUsableVector() throws Exception {
        cache.write("org.apache.struts", "struts-core", "1.3.10", "2026-01-01", array(
                vuln("GHSA-ffff-0000-0006", List.of("CVE-2020-0002"), null, "MODERATE", false)));
        Map<String, Object> out = builder(DepsBuilder.Mode.OFFLINE, NO_NETWORK).build(war, pom, REFERENCE);
        DepsBuilder.Advisory a = advisories(out).stream().filter(x -> x.id().equals("CVE-2020-0002")).findFirst().orElseThrow();
        assertEquals("MEDIUM", a.severity());
        assertEquals("ghsa-label", a.severitySource());
    }

    @Test
    void noOsvRecordsIsNotTheSameAsClean() throws Exception {
        Map<String, Object> out = builder(DepsBuilder.Mode.OFFLINE, NO_NETWORK).build(war, pom, REFERENCE);
        assertEquals("NO_RECORDS", byFile(libraries(out), "commons-logging-1.0.4.jar").osvStatus());
        assertEquals(1, summary(out).get("librariesWithNoOsvRecords"));
    }

    @Test
    void eolFrameworksAreFlaggedAgainstTheTablesOwnDate() throws Exception {
        Map<String, Object> out = builder(DepsBuilder.Mode.OFFLINE, NO_NETWORK).build(war, pom, REFERENCE);
        DepsBuilder.Library struts = byFile(libraries(out), "struts-core-1.3.10.jar");
        assertEquals("EOL", struts.eol().status());
        assertEquals("2013-04-05", struts.eol().since());
        assertNull(byFile(libraries(out), "commons-beanutils-1.8.0.jar").eol());
        assertEquals("2026-10-05", ((Map<?, ?>) out.get("provenance")).get("eolTableAsOf"));
    }

    @Test
    void providedScopeDependenciesAreInTheirOwnSection() throws Exception {
        Map<String, Object> out = builder(DepsBuilder.Mode.OFFLINE, NO_NETWORK).build(war, pom, REFERENCE);
        List<DepsBuilder.Library> provided = providedLibs(out);
        assertEquals(1, provided.size());
        assertEquals("javax.servlet-api", provided.get(0).artifactId());
        assertEquals("pom (provided scope)", provided.get(0).identifiedBy());
        assertTrue(libraries(out).stream().noneMatch(l -> "javax.servlet-api".equals(l.artifactId())),
                "provided jars are not in the WAR");
    }

    @Test
    void dataAsOfIsTakenFromTheCacheNotTheClock() throws Exception {
        Map<String, Object> out = builder(DepsBuilder.Mode.OFFLINE, NO_NETWORK).build(war, pom, REFERENCE);
        assertEquals("2026-02-01", ((Map<?, ?>) out.get("provenance")).get("dataAsOf"));
    }

    @Test
    void sameInputsGiveByteIdenticalOutput() throws Exception {
        String a = Json.toString(builder(DepsBuilder.Mode.OFFLINE, NO_NETWORK).build(war, pom, REFERENCE));
        String b = Json.toString(builder(DepsBuilder.Mode.OFFLINE, NO_NETWORK).build(war, pom, REFERENCE));
        assertEquals(a, b);
        assertFalse(a.contains(tmp.toString()), "no absolute paths in results");
    }

    @Test
    void aFailedRefreshLeavesTheCommittedCacheUntouched() throws Exception {
        Path file = cache.fileFor("org.apache.struts", "struts-core", "1.3.10");
        byte[] before = Files.readAllBytes(file);
        HttpFetcher failing = (url, body) -> new HttpFetcher.Response(500, "boom");
        DepsBuilder refresh = new DepsBuilder(cache, new OsvClient(failing, new long[] {0, 0}), DepsBuilder.Mode.REFRESH, () -> "2099-01-01");
        assertThrows(IOException.class, () -> refresh.build(war, pom, REFERENCE));
        assertEquals(new String(before), new String(Files.readAllBytes(file)));
    }

    @Test
    void autoModeUsesTheCacheAndOnlyQueriesMisses() throws Exception {
        Files.delete(cache.fileFor("commons-logging", "commons-logging", "1.0.4"));
        int[] calls = {0};
        HttpFetcher counting = (url, body) -> {
            calls[0]++;
            return new HttpFetcher.Response(200, "{}");
        };
        new DepsBuilder(cache, new OsvClient(counting), DepsBuilder.Mode.AUTO, () -> "2026-03-01").build(war, pom, REFERENCE);
        assertEquals(1, calls[0], "only commons-logging was missing");
        assertTrue(Files.exists(cache.fileFor("commons-logging", "commons-logging", "1.0.4")));
    }

    // ---- helpers

    private DepsBuilder builder(DepsBuilder.Mode mode, HttpFetcher http) {
        return new DepsBuilder(cache, new OsvClient(http, new long[] {0}), mode, () -> "2026-03-01");
    }

    private static ObjectNode vuln(String id, List<String> aliases, String vector, String ghsa, boolean withdrawn) {
        ObjectNode v = Json.MAPPER.createObjectNode();
        v.put("id", id);
        ArrayNode al = v.putArray("aliases");
        aliases.forEach(al::add);
        v.put("summary", "summary of " + id);
        if (vector != null) {
            ObjectNode s = v.putArray("severity").addObject();
            s.put("type", vector.startsWith("CVSS:4") ? "CVSS_V4" : "CVSS_V3");
            s.put("score", vector);
        }
        if (ghsa != null) {
            v.putObject("database_specific").put("severity", ghsa);
        }
        if (withdrawn) {
            v.put("withdrawn", "2025-01-01T00:00:00Z");
        }
        return v;
    }

    private static ArrayNode array(ObjectNode... vulns) {
        ArrayNode a = Json.MAPPER.createArrayNode();
        for (ObjectNode v : vulns) {
            a.add(v);
        }
        return a;
    }

    private static byte[] jar(String g, String a, String v) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            put(z, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
            if (g != null) {
                put(z, "META-INF/maven/" + g + "/" + a + "/pom.properties",
                        ("groupId=" + g + "\nartifactId=" + a + "\nversion=" + v + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        return bos.toByteArray();
    }

    private static void put(ZipOutputStream z, String name, byte[] data) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(data);
        z.closeEntry();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> summary(Map<String, Object> out) {
        return (Map<String, Object>) out.get("summary");
    }

    @SuppressWarnings("unchecked")
    private static List<DepsBuilder.Library> libraries(Map<String, Object> out) {
        return (List<DepsBuilder.Library>) out.get("libraries");
    }

    @SuppressWarnings("unchecked")
    private static List<DepsBuilder.Library> providedLibs(Map<String, Object> out) {
        return (List<DepsBuilder.Library>) out.get("provided");
    }

    @SuppressWarnings("unchecked")
    private static List<DepsBuilder.Advisory> advisories(Map<String, Object> out) {
        return (List<DepsBuilder.Advisory>) out.get("advisories");
    }

    private static DepsBuilder.Library byFile(List<DepsBuilder.Library> libs, String file) {
        return libs.stream().filter(l -> file.equals(l.file())).findFirst().orElseThrow();
    }
}
