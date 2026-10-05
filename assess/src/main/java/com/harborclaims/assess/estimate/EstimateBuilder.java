package com.harborclaims.assess.estimate;

import com.fasterxml.jackson.databind.JsonNode;
import com.harborclaims.assess.common.Hashing;
import com.harborclaims.assess.common.Modules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Builds estimate.json: phases, module scores, totals, calendar time, sensitivity and the assumptions behind it all. */
public final class EstimateBuilder {

    public record Sensitivity(String parameter, BigDecimal halfEffectOnMidpoint, BigDecimal oneAndHalfEffectOnMidpoint) {
    }

    public record Assumption(String group, String item, BigDecimal low, BigDecimal high, String rationale) {
    }

    private EstimateBuilder() {
    }

    public static Map<String, Object> build(JsonNode inventory, JsonNode structure, JsonNode security, JsonNode deps,
            Modules modules, Model model, String modelSha256, String modulesSha256) {
        Facts facts = Facts.build(inventory, structure, security, deps, modules, model);
        EstimateEngine.Result base = EstimateEngine.estimate(model, facts, EstimateEngine.Options.DEFAULT);

        // ---- calendar time from the team shape
        BigDecimal perWeek = BigDecimal.valueOf(model.team().developers()).multiply(BigDecimal.valueOf(5)).multiply(model.team().focusFactor());
        Range weeks = new Range(base.total().low().divide(perWeek, 6, RoundingMode.HALF_UP),
                base.total().high().divide(perWeek, 6, RoundingMode.HALF_UP)).roundedOutward(model.roundingStepDays());

        // ---- sensitivity: every rate, surcharge and fixed cost at half and at one and a half times
        List<Sensitivity> sens = new ArrayList<>();
        for (String group : List.of("rates", "surcharges", "fixed")) {
            for (String key : parameters(model, group)) {
                BigDecimal down = delta(model, facts, base, group, key, new BigDecimal("0.5"));
                BigDecimal up = delta(model, facts, base, group, key, new BigDecimal("1.5"));
                sens.add(new Sensitivity(group + "." + key, down, up));
            }
        }
        sens.sort(Comparator.comparing((Sensitivity s) -> s.halfEffectOnMidpoint().abs().max(s.oneAndHalfEffectOnMidpoint().abs()))
                .reversed().thenComparing(Sensitivity::parameter));

        // ---- the per-mapping alternative (some action classes are mapped twice)
        EstimateEngine.Result perMapping = EstimateEngine.estimate(model, facts, new EstimateEngine.Options(true));
        Map<String, Object> alt = new TreeMap<>();
        alt.put("description", "Charge the action-class rate per non-forward mapping instead of per distinct class");
        alt.put("totalLow", perMapping.total().low());
        alt.put("totalHigh", perMapping.total().high());
        alt.put("deltaLow", perMapping.total().low().subtract(base.total().low()));
        alt.put("deltaHigh", perMapping.total().high().subtract(base.total().high()));

        Range bigBang = EstimateEngine.bigBang(model, facts);
        Map<String, Object> scenarios = new TreeMap<>();
        Map<String, Object> strangler = new TreeMap<>();
        strangler.put("low", base.total().low());
        strangler.put("high", base.total().high());
        strangler.put("note", "Phases above: security fixes, platform and first slice, then module by module behind a gateway.");
        Map<String, Object> bb = new TreeMap<>();
        bb.put("low", bigBang.low());
        bb.put("high", bigBang.high());
        bb.put("note", "Platform and harness plus every module in full (leave-alone modules included), every God class and cycle edge. "
                + "No gateway, auth bridge or Phase 0 fixes, because nothing runs side by side.");
        Range excludedSum = Range.ZERO;
        for (EstimateEngine.Excluded ex : base.excluded()) {
            excludedSum = excludedSum.add(ex.effort());
        }
        Map<String, Object> bbSame = new TreeMap<>();
        bbSame.put("low", bigBang.low().subtract(excludedSum.low()));
        bbSame.put("high", bigBang.high().subtract(excludedSum.high()));
        bbSame.put("note", "The big-bang figure minus the modules the strangler plan leaves alone, so both cover the same modules.");
        scenarios.put("bigBangRewriteSameModules", bbSame);
        scenarios.put("strangler", strangler);
        scenarios.put("bigBangRewrite", bb);

        Map<String, Object> totals = new TreeMap<>();
        totals.put("low", base.total().low());
        totals.put("high", base.total().high());
        totals.put("unit", model.unit());
        totals.put("calendarWeeksLow", weeks.low());
        totals.put("calendarWeeksHigh", weeks.high());
        totals.put("developers", model.team().developers());
        totals.put("focusFactor", model.team().focusFactor());
        totals.put("includesExcludedModules", false);

        List<String> fp = List.of("inventory:" + inventory.get("inputsFingerprint").asText(),
                "structure:" + structure.get("inputsFingerprint").asText(),
                "security:" + security.get("inventoryFingerprint").asText() + ":" + security.get("semgrep").get("inputsFingerprint").asText(),
                "deps:" + deps.get("inputsFingerprint").asText(), "model:" + modelSha256, "modules:" + modulesSha256);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schema", 1);
        out.put("tool", "assess 1.0.0");
        out.put("source", "legacy-app");
        out.put("inputsFingerprint", Hashing.fingerprint(fp));
        out.put("modelFingerprint", modelSha256);
        out.put("totals", totals);
        out.put("phases", base.phases());
        out.put("moduleScores", base.scores());
        out.put("excludedModules", base.excluded());
        out.put("sensitivity", sens);
        out.put("alternativePerMapping", alt);
        out.put("scenarios", scenarios);
        out.put("assumptions", assumptions(model));
        out.put("notes", List.of(
                "Effort is in " + model.unit() + ". Bands add lows and highs separately: a hard band, not a confidence interval.",
                "Rows are rounded outward to " + model.roundingStepDays().stripTrailingZeros().toPlainString()
                        + " day; every total is the sum of the rows shown.",
                "Modules left alone are listed with their cost and are not in the total.",
                "Phase 0 fixes in legacy are partly thrown away when the code is rewritten: deliberate double work.",
                "Business values, rates and multipliers are labelled assumptions in the estimation model."));
        return out;
    }

    private static List<String> parameters(Model m, String group) {
        Map<String, Model.Rate> map = group.equals("rates") ? m.rates() : group.equals("surcharges") ? m.surcharges() : m.fixed();
        return new ArrayList<>(new java.util.TreeSet<>(map.keySet()));
    }

    private static BigDecimal delta(Model model, Facts facts, EstimateEngine.Result base, String group, String key, BigDecimal factor) {
        EstimateEngine.Result r = EstimateEngine.estimate(scaled(model, group, key, factor), facts, EstimateEngine.Options.DEFAULT);
        return r.total().midpoint().subtract(base.total().midpoint()).setScale(1, RoundingMode.HALF_UP);
    }

    /** A copy of the model with one parameter's low and high multiplied by factor. */
    static Model scaled(Model m, String group, String key, BigDecimal factor) {
        Map<String, Model.Rate> rates = group.equals("rates") ? replace(m.rates(), key, factor) : m.rates();
        Map<String, Model.Rate> surcharges = group.equals("surcharges") ? replace(m.surcharges(), key, factor) : m.surcharges();
        Map<String, Model.Rate> fixed = group.equals("fixed") ? replace(m.fixed(), key, factor) : m.fixed();
        return new Model(m.unit(), m.roundingStepDays(), m.team(), rates, m.multipliers(), surcharges, fixed, m.phase0(), m.detect(),
                m.businessValue(), m.scoring());
    }

    private static Map<String, Model.Rate> replace(Map<String, Model.Rate> map, String key, BigDecimal factor) {
        Map<String, Model.Rate> out = new TreeMap<>(map);
        Model.Rate r = map.get(key);
        out.put(key, new Model.Rate(r.low().multiply(factor), r.high().multiply(factor), r.rationale()));
        return out;
    }

    /** The assumptions section of the report is rendered from this list, which comes straight from the model file. */
    static List<Assumption> assumptions(Model m) {
        List<Assumption> out = new ArrayList<>();
        new TreeMap<>(m.rates()).forEach((k, r) -> out.add(new Assumption("Unit rates", k, r.low(), r.high(), r.rationale())));
        new TreeMap<>(m.surcharges()).forEach((k, r) -> out.add(new Assumption("One-off surcharges", k, r.low(), r.high(), r.rationale())));
        new TreeMap<>(m.fixed()).forEach((k, r) -> out.add(new Assumption("Fixed costs", k, r.low(), r.high(), r.rationale())));
        new TreeMap<>(m.multipliers().factors()).forEach((k, f) -> out.add(new Assumption("Multipliers (additive, cap "
                + m.multipliers().cap().toPlainString() + ")", k, f.value(), f.value(), f.rationale())));
        new TreeMap<>(m.phase0().securityCategories()).forEach((k, c) -> {
            out.add(new Assumption("Phase 0 security fixes", k + " base", c.base().low(), c.base().high(), c.base().rationale()));
            if (c.perSite().high().signum() > 0) {
                out.add(new Assumption("Phase 0 security fixes", k + " per site", c.perSite().low(), c.perSite().high(), c.perSite().rationale()));
            }
        });
        out.add(new Assumption("Phase 0 dependencies", "upgrade per library family", m.phase0().dependencyUpgradePerFamily().low(),
                m.phase0().dependencyUpgradePerFamily().high(), m.phase0().dependencyUpgradePerFamily().rationale()));
        out.add(new Assumption("Phase 0 dependencies", "compensating controls", m.phase0().compensatingControls().low(),
                m.phase0().compensatingControls().high(), m.phase0().compensatingControls().rationale()));
        new TreeMap<>(m.businessValue()).forEach((k, v) -> out.add(new Assumption("Business value (1 to 5)", k,
                BigDecimal.valueOf(v.value()), BigDecimal.valueOf(v.value()), v.rationale())));
        out.add(new Assumption("Team", "developers and focus factor", BigDecimal.valueOf(m.team().developers()), m.team().focusFactor(),
                m.team().rationale()));
        return out;
    }
}
