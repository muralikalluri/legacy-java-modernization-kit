package com.harborclaims.assess.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.harborclaims.assess.common.Json;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FullReportTest {

    private static final Path TEMPLATE = Path.of("templates/full-assessment.md.mustache");
    private static final Path QUICK_TEMPLATE = Path.of("templates/quick-assessment.md.mustache");
    private static final Path MAPPING = Path.of("reference/framework-mapping.yaml");
    private static final Path RESULTS = Path.of("results");

    private static String render(JsonNode inventory, JsonNode estimate) throws Exception {
        JsonNode deps = Json.read(RESULTS.resolve("deps.json"));
        JsonNode jakarta = Json.read(RESULTS.resolve("jakarta.json"));
        JsonNode structure = Json.read(RESULTS.resolve("structure.json"));
        JsonNode security = Json.read(RESULTS.resolve("security.json"));
        ReportBuilder.Output quick = ReportBuilder.build(Files.readString(QUICK_TEMPLATE, StandardCharsets.UTF_8),
                RiskRules.load(Path.of("reference/risk-rules.yaml")), inventory, deps, jakarta, structure, security, estimate);
        return FullReportBuilder.build(Files.readString(TEMPLATE, StandardCharsets.UTF_8), quick.viewModel(), quick.risksJson(),
                FullReportBuilder.loadMapping(MAPPING), inventory, deps, jakarta, structure, security, estimate);
    }

    private static String render() throws Exception {
        return render(Json.read(RESULTS.resolve("inventory.json")), Json.read(RESULTS.resolve("estimate.json")));
    }

    @Test
    void theTemplateContainsNoDigitsSoEveryNumberComesFromData() throws Exception {
        assertFalse(Pattern.compile("[0-9]").matcher(Files.readString(TEMPLATE, StandardCharsets.UTF_8)).find());
    }

    @Test
    void theCommittedFullReportIsExactlyWhatTheToolGenerates() throws Exception {
        String committed = Files.readString(Path.of("..", "sample-deliverable", "ASSESSMENT_AND_ROADMAP.md"), StandardCharsets.UTF_8);
        assertEquals(render(), committed, "ASSESSMENT_AND_ROADMAP.md was edited by hand or is stale: run ./assess/run-all.sh");
    }

    @Test
    void renderingIsDeterministicPlainAsciiAndIndependentOfLocaleAndZone() throws Exception {
        String first = render();
        Locale oldLocale = Locale.getDefault();
        TimeZone oldZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            assertEquals(first, render());
        } finally {
            Locale.setDefault(oldLocale);
            TimeZone.setDefault(oldZone);
        }
        assertTrue(first.endsWith("\n"));
        assertFalse(first.contains("\r"));
        assertTrue(first.chars().allMatch(c -> c < 128));
    }

    @Test
    void everySectionTheSpecAsksForIsPresent() throws Exception {
        String md = render();
        for (String h : List.of("## Executive summary", "## System inventory", "## Risk register", "## Framework mapping",
                "## Module scoring and migration order", "## Target architecture", "## Phased roadmap", "## Effort estimate",
                "## Security gaps to close during migration", "## Appendix: raw tool outputs")) {
            assertTrue(md.contains(h), "missing " + h);
        }
        assertEquals(2, md.split("```mermaid", -1).length - 1, "target architecture and phase flow");
    }

    @Test
    void phaseTotalsAddUpToTheStatedTotal() throws Exception {
        JsonNode estimate = Json.read(RESULTS.resolve("estimate.json"));
        BigDecimal low = BigDecimal.ZERO;
        BigDecimal high = BigDecimal.ZERO;
        for (JsonNode p : estimate.get("phases")) {
            low = low.add(p.get("total").get("low").decimalValue());
            high = high.add(p.get("total").get("high").decimalValue());
        }
        assertEquals(0, low.compareTo(estimate.get("totals").get("low").decimalValue()));
        assertEquals(0, high.compareTo(estimate.get("totals").get("high").decimalValue()));
    }

    @Test
    void numbersFlowFromTheResultsNotFromTheCode() throws Exception {
        JsonNode inventory = Json.read(RESULTS.resolve("inventory.json"));
        JsonNode estimate = Json.read(RESULTS.resolve("estimate.json"));
        JsonNode inv2 = inventory.deepCopy();
        ((ObjectNode) inv2.get("summary")).put("daos", 4321);
        assertTrue(render(inv2, estimate).contains("4321 DAOs"), "the DAO count in the mapping table comes from inventory.json");
        JsonNode est2 = estimate.deepCopy();
        ((ObjectNode) est2.get("alternativePerMapping")).put("totalHigh", new BigDecimal("8765.5"));
        assertTrue(render(inventory, est2).contains("8765.5"), "the alternative total comes from estimate.json");
    }

    @Test
    void anUnknownCountKeyInTheMappingFailsLoudly() throws Exception {
        Path bad = Files.createTempFile("mapping", ".yaml");
        try {
            Files.writeString(bad, "mappings:\n  - legacy: a\n    target: b\n    notes: c\n    count: NOPE\n");
            JsonNode inv = Json.read(RESULTS.resolve("inventory.json"));
            JsonNode deps = Json.read(RESULTS.resolve("deps.json"));
            JsonNode jakarta = Json.read(RESULTS.resolve("jakarta.json"));
            JsonNode structure = Json.read(RESULTS.resolve("structure.json"));
            JsonNode security = Json.read(RESULTS.resolve("security.json"));
            JsonNode estimate = Json.read(RESULTS.resolve("estimate.json"));
            ReportBuilder.Output quick = ReportBuilder.build(Files.readString(QUICK_TEMPLATE, StandardCharsets.UTF_8),
                    RiskRules.load(Path.of("reference/risk-rules.yaml")), inv, deps, jakarta, structure, security, estimate);
            assertThrows(IllegalArgumentException.class, () -> FullReportBuilder.build(Files.readString(TEMPLATE, StandardCharsets.UTF_8),
                    quick.viewModel(), quick.risksJson(), FullReportBuilder.loadMapping(bad), inv, deps, jakarta, structure, security, estimate));
        } finally {
            Files.deleteIfExists(bad);
        }
    }

    @Test
    void theReadmeResultsBlockIsExactlyWhatTheToolGenerates() throws Exception {
        String readme = Files.readString(Path.of("..", "README.md"), StandardCharsets.UTF_8);
        String block = FullReportBuilder.readmeResults(Json.read(RESULTS.resolve("inventory.json")), Json.read(RESULTS.resolve("deps.json")),
                Json.read(RESULTS.resolve("jakarta.json")), Json.read(RESULTS.resolve("structure.json")), Json.read(RESULTS.resolve("security.json")),
                Json.read(RESULTS.resolve("estimate.json")));
        assertEquals(readme, FullReportBuilder.replaceReadmeBlock(readme, block), "README results block is hand-edited or stale: run ./assess/run-all.sh");
    }

    @Test
    void theReadmeFollowsTheRequiredSectionOrder() throws Exception {
        String readme = Files.readString(Path.of("..", "README.md"), StandardCharsets.UTF_8);
        int last = -1;
        for (String marker : List.of("![CI]", "strangler-demo.gif", "## Results", "```mermaid", "## Quickstart", "## Features", "## Design decisions",
                "## Sample deliverable", "## Hire me")) {
            int at = readme.indexOf(marker.startsWith("![CI]") ? "actions/workflows/ci.yml/badge.svg" : marker);
            assertTrue(at > last, marker + " is missing or out of order");
            last = at;
        }
    }

    @Test
    void aReadmeWithoutMarkersFailsLoudly() {
        assertThrows(IllegalArgumentException.class, () -> FullReportBuilder.replaceReadmeBlock("no markers here", "x"));
    }
}
