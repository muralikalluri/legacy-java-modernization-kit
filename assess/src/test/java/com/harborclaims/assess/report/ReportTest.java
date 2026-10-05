package com.harborclaims.assess.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.harborclaims.assess.common.Json;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ReportTest {

    private static final Path TEMPLATE = Path.of("templates/quick-assessment.md.mustache");
    private static final Path RESULTS = Path.of("results");

    private static ReportBuilder.Output render() throws Exception {
        return ReportBuilder.build(Files.readString(TEMPLATE, StandardCharsets.UTF_8), RiskRules.load(Path.of("reference/risk-rules.yaml")),
                Json.read(RESULTS.resolve("inventory.json")), Json.read(RESULTS.resolve("deps.json")), Json.read(RESULTS.resolve("jakarta.json")),
                Json.read(RESULTS.resolve("structure.json")), Json.read(RESULTS.resolve("security.json")), Json.read(RESULTS.resolve("estimate.json")));
    }

    @Test
    void theTemplateContainsNoDigitsSoEveryNumberComesFromData() throws Exception {
        String template = Files.readString(TEMPLATE, StandardCharsets.UTF_8);
        assertFalse(Pattern.compile("[0-9]").matcher(template).find(), "a digit in the template would be a hand-typed number");
    }

    @Test
    void aMissingVariableFailsInsteadOfRenderingEmpty() {
        assertThrows(MustacheException.class, () -> Mustache.compiler().escapeHTML(false).compile("value: {{nope}}").execute(Map.of()));
    }

    @Test
    void escapingKeepsTableCellsIntact() {
        assertEquals("a \\| b", Md.text("a | b"));
        assertEquals("Entity Reference in iText", Md.text("Entity Reference in  iText"));
        assertEquals("line one line two", Md.text("line one\nline two"));
        assertEquals("snake\\_case \\`code\\` \\*bold\\* \\<tag\\>", Md.text("snake_case `code` *bold* <tag>"));
        assertEquals("", Md.text(null));
    }

    @Test
    void numberFormattingIsPlainAndStable() {
        assertEquals("225", Md.num(new BigDecimal("225.0")));
        assertEquals("88.5", Md.num(new BigDecimal("88.50")));
        assertEquals("100", Md.num(new BigDecimal("1E+2")));
        assertEquals("88.5 to 225", Md.range(new BigDecimal("88.5"), new BigDecimal("225.0")));
        assertEquals("4", Md.range(new BigDecimal("4.0"), new BigDecimal("4")));
        assertEquals("1 site", Md.plural(1, "site", "sites"));
        assertEquals("3 sites", Md.plural(3, "site", "sites"));
        assertEquals("1 query site", Md.unit(1, "query sites"));
        assertEquals("3 query sites", Md.unit(3, "query sites"));
        assertEquals("0 actions", Md.unit(0, "actions"));
    }

    @Test
    void renderingTwiceAndUnderAnotherLocaleAndTimeZoneGivesIdenticalBytes() throws Exception {
        String first = render().markdown();
        assertEquals(first, render().markdown());
        Locale oldLocale = Locale.getDefault();
        TimeZone oldZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            assertEquals(first, render().markdown());
        } finally {
            Locale.setDefault(oldLocale);
            TimeZone.setDefault(oldZone);
        }
        assertTrue(first.endsWith("\n"));
        assertFalse(first.contains("\r"));
        assertFalse(first.startsWith("﻿"));
        assertTrue(first.chars().allMatch(c -> c < 128), "plain ASCII, so hyphens and quotes cannot drift");
    }

    @Test
    void theCommittedSampleDeliverableIsExactlyWhatTheToolGenerates() throws Exception {
        String committed = Files.readString(Path.of("..", "sample-deliverable", "QUICK_ASSESSMENT.md"), StandardCharsets.UTF_8);
        assertEquals(render().markdown(), committed, "QUICK_ASSESSMENT.md was edited by hand or is stale: run ./assess/run-all.sh");
    }

    @Test
    void numbersFlowFromTheResultsNotFromTheCode() throws Exception {
        JsonNode inventory = Json.read(RESULTS.resolve("inventory.json"));
        JsonNode deps = Json.read(RESULTS.resolve("deps.json"));
        JsonNode estimate = Json.read(RESULTS.resolve("estimate.json"));
        String before = build(inventory, deps, estimate).markdown();

        // change one input number in each results file and the report must follow it
        JsonNode inv2 = inventory.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) inv2.get("modules").get(0)).put("javaMainLines", 7654321);
        assertTrue(build(inv2, deps, estimate).markdown().contains("7654321"), "module line count comes from inventory.json");
        assertTrue(!before.contains("7654321"));

        JsonNode deps2 = deps.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) deps2.get("summary")).put("jarsInWar", 4242);
        assertTrue(build(inventory, deps2, estimate).markdown().contains("4242 jars"), "jar count comes from deps.json");

        JsonNode est2 = estimate.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) est2.get("totals")).put("high", new BigDecimal("9999.5"));
        assertTrue(build(inventory, deps, est2).markdown().contains("9999.5"), "the effort band comes from estimate.json");
    }

    private static ReportBuilder.Output build(JsonNode inventory, JsonNode deps, JsonNode estimate) throws Exception {
        return ReportBuilder.build(Files.readString(TEMPLATE, StandardCharsets.UTF_8), RiskRules.load(Path.of("reference/risk-rules.yaml")),
                inventory, deps, Json.read(RESULTS.resolve("jakarta.json")), Json.read(RESULTS.resolve("structure.json")),
                Json.read(RESULTS.resolve("security.json")), estimate);
    }

    @Test
    void theJavaxFileCountIsTheFilesThatActuallyMoveToJakarta() throws Exception {
        JsonNode main = Json.read(RESULTS.resolve("jakarta.json")).get("javaMain");
        int moving = main.get("filesWithJakartaMigratingJavax").asInt();
        assertTrue(moving < main.get("filesWithJavax").asInt(), "a file with only javax.naming or javax.sql stays javax");
        assertTrue(render().markdown().contains("javax.servlet imports in " + moving + " files"));
        assertTrue(render().markdown().contains("imports in " + moving + " files)"));
    }

    @Test
    void theStrutsCaveatAndTheAssumptionsAreRenderedFromTheData() throws Exception {
        String md = render().markdown();
        JsonNode notes = Json.read(RESULTS.resolve("jakarta.json")).get("notes");
        String caveat = "";
        for (JsonNode n : notes) {
            if (n.asText().startsWith("There is no Jakarta-compatible Struts 1")) {
                caveat = n.asText();
            }
        }
        assertFalse(caveat.isEmpty());
        assertTrue(md.contains(Md.text(caveat)), "the caveat is quoted from jakarta.json, not retyped");
        assertTrue(md.contains("## Caveats and assumptions"));
        assertTrue(md.contains("assess/estimation-model.yaml"));
        for (String heading : new String[] {"## System inventory", "## Top risks", "## Dependency and CVE summary", "## Recommended approach",
                "## Rough effort band"}) {
            assertTrue(md.contains(heading), heading);
        }
    }

    @Test
    void theRiskRegisterJsonIsDeterministicAndConsistentWithTheTopList() throws Exception {
        ReportBuilder.Output o = render();
        assertEquals(Json.toString(o.risksJson()), Json.toString(render().risksJson()));
        @SuppressWarnings("unchecked")
        java.util.List<String> topIds = (java.util.List<String>) o.risksJson().get("topIds");
        assertEquals(10, topIds.size());
        @SuppressWarnings("unchecked")
        java.util.List<String> omitted = (java.util.List<String>) o.risksJson().get("omittedByCap");
        assertTrue(java.util.Collections.disjoint(topIds, omitted));
    }
}
