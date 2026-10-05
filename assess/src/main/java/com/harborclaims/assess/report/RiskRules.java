package com.harborclaims.assess.report;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Likelihood and impact inputs for the risk register (assess/reference/risk-rules.yaml). Unknown keys are rejected. */
public record RiskRules(int topN, int perGroupCap, List<String> groupOrder, Map<String, Cell> securityCategories,
        Map<String, Level> dependencySeverity, Map<String, Level> eolStatus, Map<String, Level> structure,
        Map<String, Level> platform, List<Knowledge> knowledge) {

    public record Level(int likelihood, int impact) {
    }

    public record Cell(int likelihood, int impact, String rationale) {
    }

    public record Knowledge(String id, String title, int likelihood, int impact, boolean assumed, String rationale) {
    }

    public static RiskRules load(Path yaml) throws IOException {
        RiskRules r = new ObjectMapper(new YAMLFactory()).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(Files.readAllBytes(yaml), RiskRules.class);
        r.validate();
        return r;
    }

    public void validate() {
        if (topN < 1 || perGroupCap < 1) {
            throw new IllegalArgumentException("risk rules: topN and perGroupCap must be positive");
        }
        securityCategories.forEach((k, c) -> level("securityCategories." + k, c.likelihood(), c.impact()));
        dependencySeverity.forEach((k, l) -> level("dependencySeverity." + k, l.likelihood(), l.impact()));
        eolStatus.forEach((k, l) -> level("eolStatus." + k, l.likelihood(), l.impact()));
        structure.forEach((k, l) -> level("structure." + k, l.likelihood(), l.impact()));
        platform.forEach((k, l) -> level("platform." + k, l.likelihood(), l.impact()));
        knowledge.forEach(k -> level("knowledge." + k.id(), k.likelihood(), k.impact()));
        if (!dependencySeverity.containsKey("UNKNOWN")) {
            throw new IllegalArgumentException("risk rules: dependencySeverity.UNKNOWN must be defined (never treated as zero)");
        }
    }

    private static void level(String name, int l, int i) {
        if (l < 1 || l > 5 || i < 1 || i > 5) {
            throw new IllegalArgumentException("risk rules: " + name + " must be 1 to 5");
        }
    }
}
