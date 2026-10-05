package com.harborclaims.assess.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.harborclaims.assess.common.Json;
import com.harborclaims.assess.report.RiskBuilder.Risk;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskBuilderTest {

    private static RiskRules rules() throws Exception {
        return RiskRules.load(Path.of("reference/risk-rules.yaml"));
    }

    private static JsonNode security(int sqlSites) throws Exception {
        return Json.MAPPER.readTree("{\"findings\":[{\"category\":\"SQL_INJECTION\",\"title\":\"SQL built by string concatenation\","
                + "\"cwe\":\"CWE-89\",\"count\":" + sqlSites + ",\"countUnit\":\"query sites\",\"locations\":[]}]}");
    }

    private static final JsonNode STRUCTURE = parse("{\"largestClasses\":[],\"packageCycles\":[]}");
    private static final JsonNode JAKARTA = parse("{\"javaMain\":{\"jakartaMigratingImports\":9,\"filesWithJavax\":5,\"filesWithJakartaMigratingJavax\":4},\"rewrite\":{\"changedFiles\":3}}");

    private static JsonNode parse(String s) {
        try {
            return Json.MAPPER.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String lib(String group, String artifact, String version, String advisories, String eol) {
        return "{\"groupId\":\"" + group + "\",\"artifactId\":\"" + artifact + "\",\"version\":\"" + version + "\",\"advisoryIds\":["
                + advisories + "],\"eol\":" + eol + "}";
    }

    private static final String EOL_STRUTS = "{\"status\":\"EOL\",\"since\":\"2013-04-05\"}";

    private static JsonNode deps(List<String> libs, String provided, String advisories) {
        return parse("{\"libraries\":[" + String.join(",", libs) + "],\"provided\":[" + provided + "],\"advisories\":[" + advisories + "]}");
    }

    private static String adv(String id, String severity) {
        return "{\"id\":\"" + id + "\",\"severity\":\"" + severity + "\"}";
    }

    @Test
    void threeSqlSitesAreOneRiskNotThree() throws Exception {
        List<Risk> r = RiskBuilder.register(rules(), security(3), deps(List.of(), "", ""), STRUCTURE, JAKARTA);
        List<Risk> sql = r.stream().filter(x -> x.id().equals("SEC-SQL_INJECTION")).toList();
        assertEquals(1, sql.size());
        assertEquals("3 query sites", sql.get(0).evidence());
        assertEquals(25, sql.get(0).score());
    }

    @Test
    void oneSiteIsSingular() throws Exception {
        List<Risk> r = RiskBuilder.register(rules(), security(1), deps(List.of(), "", ""), STRUCTURE, JAKARTA);
        assertEquals("1 query site", r.stream().filter(x -> x.id().equals("SEC-SQL_INJECTION")).findFirst().orElseThrow().evidence());
    }

    @Test
    void eolAndCvesOnOneFamilyAreOneRiskWithTheHigherScore() throws Exception {
        JsonNode d = deps(List.of(lib("org.apache.struts", "struts-core", "1.3.10", "\"CVE-1\"", EOL_STRUTS),
                lib("org.apache.struts", "struts-tiles", "1.3.10", "\"CVE-2\"", EOL_STRUTS)), "", adv("CVE-1", "HIGH") + "," + adv("CVE-2", "MEDIUM"));
        List<Risk> deps = RiskBuilder.register(rules(), security(0), d, STRUCTURE, JAKARTA).stream().filter(x -> x.group().equals("Dependencies")).toList();
        assertEquals(1, deps.size());
        assertEquals(16, deps.get(0).score(), "end of life (4 x 4) beats the worst advisory (high: 3 x 4)");
        assertTrue(deps.get(0).evidence().contains("2 advisories") && deps.get(0).evidence().contains("end of life since 2013-04-05"));
        assertTrue(deps.get(0).title().contains("struts-core") && deps.get(0).title().contains("struts-tiles"));
    }

    @Test
    void sameGroupLibrariesFormOneFamily() throws Exception {
        JsonNode d = deps(List.of(lib("org.bouncycastle", "bcprov", "1.38", "\"CVE-1\"", "null"), lib("org.bouncycastle", "bcmail", "1.38", "\"CVE-2\"", "null")),
                "", adv("CVE-1", "HIGH") + "," + adv("CVE-2", "HIGH"));
        assertEquals(1, RiskBuilder.register(rules(), security(0), d, STRUCTURE, JAKARTA).stream().filter(x -> x.group().equals("Dependencies")).count());
    }

    @Test
    void providedLibrariesAreTheirOwnLabelledRisk() throws Exception {
        JsonNode d = deps(List.of(), lib("org.postgresql", "postgresql", "42.2.27", "\"CVE-9\"", "null"), adv("CVE-9", "CRITICAL"));
        Risk r = RiskBuilder.register(rules(), security(0), d, STRUCTURE, JAKARTA).stream().filter(x -> x.group().equals("Dependencies")).findFirst().orElseThrow();
        assertTrue(r.id().startsWith("DEP-PROVIDED-"));
        assertTrue(r.title().contains("provided by the container"));
    }

    @Test
    void unknownSeverityIsNeverZero() throws Exception {
        JsonNode d = deps(List.of(lib("a", "lib", "1", "\"X\"", "null")), "", adv("X", "UNKNOWN"));
        Risk r = RiskBuilder.register(rules(), security(0), d, STRUCTURE, JAKARTA).stream().filter(x -> x.group().equals("Dependencies")).findFirst().orElseThrow();
        assertEquals(9, r.score());
    }

    @Test
    void shuffledInputGivesTheSameOrder() throws Exception {
        List<String> libs = new ArrayList<>(List.of(lib("g1", "a", "1", "\"A\"", "null"), lib("g2", "b", "1", "\"B\"", "null"),
                lib("g3", "c", "1", "\"C\"", "null"), lib("g4", "d", "1", "\"D\"", "null")));
        String advisories = adv("A", "HIGH") + "," + adv("B", "HIGH") + "," + adv("C", "HIGH") + "," + adv("D", "HIGH");
        List<String> first = ids(RiskBuilder.register(rules(), security(3), deps(libs, "", advisories), STRUCTURE, JAKARTA));
        Collections.reverse(libs);
        assertEquals(first, ids(RiskBuilder.register(rules(), security(3), deps(libs, "", advisories), STRUCTURE, JAKARTA)));
    }

    private static List<String> ids(List<Risk> r) {
        return r.stream().map(Risk::id).toList();
    }

    @Test
    void ties_sortByImpactThenGroupOrderThenId() throws Exception {
        // SEC missing-authorization is 5 x 4 = 20 (impact 4); a critical advisory is 4 x 5 = 20 (impact 5): higher impact first
        JsonNode sec = parse("{\"findings\":[{\"category\":\"MISSING_AUTHORIZATION\",\"title\":\"t\",\"cwe\":\"CWE-862\",\"count\":2,\"countUnit\":\"actions\",\"locations\":[]}]}");
        JsonNode d = deps(List.of(lib("g", "lib", "1", "\"X\"", "null")), "", adv("X", "CRITICAL"));
        List<Risk> r = RiskBuilder.register(rules(), sec, d, STRUCTURE, JAKARTA);
        assertEquals(20, r.get(0).score());
        assertEquals(20, r.get(1).score());
        assertEquals("Dependencies", r.get(0).group());
        assertEquals("Application security", r.get(1).group());
    }

    @Test
    void theTopListCapsEachGroupAndReportsWhatTheCapDropped() throws Exception {
        List<String> libs = new ArrayList<>();
        StringBuilder advs = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            libs.add(lib("g" + i, "lib" + i, "1", "\"A" + i + "\"", "null"));
            advs.append(i > 0 ? "," : "").append(adv("A" + i, "CRITICAL"));
        }
        List<Risk> reg = RiskBuilder.register(rules(), security(3), deps(libs, "", advs.toString()), STRUCTURE, JAKARTA);
        RiskBuilder.Selection sel = RiskBuilder.top(reg, rules());
        assertEquals(3, sel.top().stream().filter(x -> x.group().equals("Dependencies")).count());
        assertEquals(2, sel.omittedByCap().size());
        assertTrue(sel.top().size() <= rules().topN());
    }

    @Test
    void tiesAcrossTheCutoffAreCounted() throws Exception {
        RiskRules base = rules();
        RiskRules two = new RiskRules(2, 5, base.groupOrder(), base.securityCategories(), base.dependencySeverity(), base.eolStatus(), base.structure(),
                base.platform(), base.knowledge());
        List<String> libs = List.of(lib("g1", "a", "1", "\"A\"", "null"), lib("g2", "b", "1", "\"B\"", "null"), lib("g3", "c", "1", "\"C\"", "null"));
        String advisories = adv("A", "CRITICAL") + "," + adv("B", "CRITICAL") + "," + adv("C", "CRITICAL");
        List<Risk> reg = RiskBuilder.register(two, security(0), deps(libs, "", advisories), STRUCTURE, JAKARTA);
        RiskBuilder.Selection sel = RiskBuilder.top(reg, two);
        assertEquals(2, sel.top().size());
        assertTrue(sel.moreAtCutoffScore() >= 1, "the third critical family has the same score as the last one shown");
    }

    @Test
    void aMissingSecurityCategoryRuleIsAnError() throws Exception {
        JsonNode sec = parse("{\"findings\":[{\"category\":\"BRAND_NEW\",\"title\":\"t\",\"cwe\":\"CWE-1\",\"count\":1,\"countUnit\":\"sites\",\"locations\":[]}]}");
        assertThrows(IllegalArgumentException.class, () -> RiskBuilder.register(rules(), sec, deps(List.of(), "", ""), STRUCTURE, JAKARTA));
    }

    @Test
    void riskRulesRejectUnknownKeysAndOutOfRangeLevels() throws Exception {
        RiskRules base = rules();
        java.util.Map<String, RiskRules.Level> bad = new java.util.TreeMap<>(base.dependencySeverity());
        bad.put("HIGH", new RiskRules.Level(9, 1));
        assertThrows(IllegalArgumentException.class, () -> new RiskRules(10, 3, base.groupOrder(), base.securityCategories(), bad,
                base.eolStatus(), base.structure(), base.platform(), base.knowledge()).validate());
    }
}
