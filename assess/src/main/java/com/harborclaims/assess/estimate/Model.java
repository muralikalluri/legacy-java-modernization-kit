package com.harborclaims.assess.estimate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.harborclaims.assess.common.Hashing;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** The estimation model file, typed. Unknown keys are rejected, so a typo cannot silently change an estimate. */
public record Model(String unit, BigDecimal roundingStepDays, Team team, Map<String, Rate> rates, Multipliers multipliers,
        Map<String, Rate> surcharges, Map<String, Rate> fixed, Phase0 phase0, Detect detect,
        Map<String, BusinessValue> businessValue, Scoring scoring) {

    public record Team(int developers, BigDecimal focusFactor, String rationale) {
    }

    public record Rate(BigDecimal low, BigDecimal high, String rationale) {

        public Range range() {
            return new Range(low, high);
        }
    }

    public record Factor(BigDecimal value, String rationale) {
    }

    public record Multipliers(BigDecimal cap, String rationale, Map<String, Factor> factors) {
    }

    public record CategoryCost(Rate base, Rate perSite) {
    }

    public record Phase0(String rationale, Map<String, CategoryCost> securityCategories, Rate dependencyUpgradePerFamily,
            Rate compensatingControls, List<String> noFixFamilies) {
    }

    public record Detect(String batchJobClassSuffix, String pdfReportClassSuffix) {
    }

    public record BusinessValue(int value, String rationale) {
    }

    public record Scoring(int leaveAloneMaxValue, BigDecimal couplingLowBelow, BigDecimal couplingHighFrom, String rationale) {
    }

    public static Model load(Path yaml) throws IOException {
        ObjectMapper m = new ObjectMapper(new YAMLFactory()).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        Model model = m.readValue(Files.readAllBytes(yaml), Model.class);
        model.validate();
        return model;
    }

    public static String fingerprint(Path yaml) throws IOException {
        return Hashing.sha256(yaml);
    }

    /** Throws IllegalArgumentException with the first problem found. */
    public void validate() {
        require(roundingStepDays != null && roundingStepDays.signum() > 0, "roundingStepDays must be positive");
        require(team != null && team.developers() > 0, "team.developers must be positive");
        require(team.focusFactor().signum() > 0 && team.focusFactor().compareTo(BigDecimal.ONE) <= 0, "team.focusFactor must be in (0, 1]");
        for (String k : List.of("actionClass", "mappingRouting", "jsp", "scriptletBlock", "dao", "manager", "batchJob", "pdfReport",
                "characterizationTest")) {
            require(rates.containsKey(k), "rates." + k + " is missing");
        }
        for (String k : List.of("godClass", "cycleEdge")) {
            require(surcharges.containsKey(k), "surcharges." + k + " is missing");
        }
        for (String k : List.of("platform", "gateway", "authBridge", "characterizationHarness")) {
            require(fixed.containsKey(k), "fixed." + k + " is missing");
        }
        rates.forEach((k, r) -> checkRate("rates." + k, r));
        surcharges.forEach((k, r) -> checkRate("surcharges." + k, r));
        fixed.forEach((k, r) -> checkRate("fixed." + k, r));
        checkRate("phase0.dependencyUpgradePerFamily", phase0.dependencyUpgradePerFamily());
        checkRate("phase0.compensatingControls", phase0.compensatingControls());
        phase0.securityCategories().forEach((k, c) -> {
            checkRate("phase0." + k + ".base", c.base());
            checkRate("phase0." + k + ".perSite", c.perSite());
        });
        require(multipliers.cap().signum() >= 0, "multipliers.cap must not be negative");
        multipliers.factors().forEach((k, f) -> require(f.value().signum() >= 0, "multipliers.factors." + k + " must not be negative"));
        for (String k : List.of("inCycle", "couplingMedium", "couplingHigh")) {
            require(multipliers.factors().containsKey(k), "multipliers.factors." + k + " is missing");
        }
        businessValue.forEach((k, v) -> require(v.value() >= 1 && v.value() <= 5, "businessValue." + k + " must be 1 to 5"));
        require(scoring.couplingLowBelow().compareTo(scoring.couplingHighFrom()) < 0, "couplingLowBelow must be below couplingHighFrom");
    }

    private static void checkRate(String name, Rate r) {
        require(r != null && r.low() != null && r.high() != null, name + " needs low and high");
        require(r.low().signum() >= 0, name + ".low must not be negative");
        require(r.low().compareTo(r.high()) <= 0, name + ": low must not exceed high");
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException("estimation model: " + message);
        }
    }
}
