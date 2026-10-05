package com.harborclaims.assess.estimate;

import com.harborclaims.assess.estimate.Facts.ActionFact;
import com.harborclaims.assess.estimate.Facts.JspFact;
import com.harborclaims.assess.estimate.Facts.ModuleFact;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns facts and the model into effort. A pure function: same inputs, same output.
 * Invariants (tested): a band adds lows and highs separately; rows are rounded outward and every total is the sum of
 * its displayed rows; multipliers are additive and capped and touch module build rows only; a God class and each cycle
 * edge are charged once, in the first phase that touches them; a class mapped twice is charged once.
 */
public final class EstimateEngine {

    public record Options(boolean chargeClassesPerMapping) {

        public static final Options DEFAULT = new Options(false);
    }

    public record Item(int phase, String module, String kind, String description, String quantity, Range unit,
            BigDecimal multiplier, Range effort) {
    }

    public record Phase(int id, String name, List<Item> items, Range total) {
    }

    public record Score(String module, int businessValue, BigDecimal couplingPerClass, String couplingBand, int riskSites,
            int priority, String decision, boolean inM6Scope, Integer phase, String note) {
    }

    public record Excluded(String module, String reason, Range effort) {
    }

    public record Result(List<Score> scores, List<Phase> phases, List<Excluded> excluded, Range total) {
    }

    private record Slice(String label, List<ActionFact> actions, List<JspFact> jsps, boolean withDataLayer, Set<String> classes) {
    }

    private EstimateEngine() {
    }

    public static Result estimate(Model model, Facts facts, Options options) {
        BigDecimal step = model.roundingStepDays();
        List<Score> scores = score(model, facts);
        Map<String, Score> scoreByModule = new LinkedHashMap<>();
        scores.forEach(s -> scoreByModule.put(s.module(), s));

        List<List<Item>> phaseItems = new ArrayList<>();
        List<String> phaseNames = new ArrayList<>();
        Map<String, Integer> firstPhaseOf = new TreeMap<>();

        // ---- Phase 0: security quick fixes in legacy
        List<Item> p0 = new ArrayList<>();
        phase0(model, facts, p0, step);
        phaseItems.add(p0);
        phaseNames.add("Security quick fixes in the legacy app");

        // ---- Phase 1: platform, gateway, auth bridge, harness and the first slice (the M6 scope)
        List<Item> p1 = new ArrayList<>();
        for (String k : List.of("platform", "gateway", "authBridge", "characterizationHarness")) {
            Model.Rate r = model.fixed().get(k);
            p1.add(item(1, Facts.PLATFORM, "FIXED", fixedLabel(k), "1", r.range(), BigDecimal.ONE, r.range(), step));
        }
        List<String> phase1Modules = new ArrayList<>();
        for (ModuleFact m : facts.modules()) {
            if (m.name().equals(Facts.PLATFORM) || m.actions().stream().noneMatch(ActionFact::inScope)) {
                continue;
            }
            phase1Modules.add(m.name());
            firstPhaseOf.put(m.name(), 1);
            Slice slice = firstSlice(m);
            addModuleRows(model, options, m, scoreByModule.get(m.name()), slice, 1, p1, step);
        }
        phaseItems.add(p1);
        phaseNames.add("Platform, gateway and first slice (" + String.join(", ", phase1Modules) + ")");

        // ---- Phase 2..n: remaining work of the non-excluded modules, best score first
        List<Score> later = new ArrayList<>();
        for (Score s : scores) {
            if (s.decision().equals("LEAVE_ALONE") && !phase1Modules.contains(s.module())) {
                continue;
            }
            ModuleFact m = facts.module(s.module());
            Slice rest = remainder(m);
            if (rest != null && !rest.actions().isEmpty()) {
                later.add(s);
            }
        }
        later.sort(Comparator.comparingInt(Score::priority).reversed().thenComparing(Score::module));
        int id = 2;
        for (Score s : later) {
            ModuleFact m = facts.module(s.module());
            Slice rest = remainder(m);
            List<Item> items = new ArrayList<>();
            addModuleRows(model, options, m, s, rest, id, items, step);
            firstPhaseOf.putIfAbsent(m.name(), id);
            phaseItems.add(items);
            phaseNames.add(phase1Modules.contains(m.name()) ? "Migrate the rest of " + m.name() : "Migrate " + m.name());
            id++;
        }

        // ---- one-off surcharges, charged once in the first phase that touches the thing
        for (ModuleFact m : facts.modules()) {
            Integer phase = firstPhaseOf.get(m.name());
            if (phase == null) {
                continue;
            }
            Model.Rate god = model.surcharges().get("godClass");
            for (String cls : m.godClasses()) {
                phaseItems.get(phase).add(item(phase, m.name(), "SURCHARGE", "Decompose God class " + simpleName(cls), "1",
                        god.range(), BigDecimal.ONE, god.range(), step));
            }
        }
        Model.Rate edgeRate = model.surcharges().get("cycleEdge");
        for (Facts.CycleEdge e : facts.cycleEdges()) {
            Integer a = firstPhaseOf.get(e.fromModule());
            Integer b = firstPhaseOf.get(e.toModule());
            Integer phase = a == null ? b : b == null ? a : Integer.valueOf(Math.min(a, b));
            if (phase == null) {
                continue;
            }
            phaseItems.get(phase).add(item(phase, e.fromModule(), "SURCHARGE",
                    "Break dependency " + simpleName(e.from()) + " -> " + simpleName(e.to()), "1", edgeRate.range(), BigDecimal.ONE,
                    edgeRate.range(), step));
        }

        List<Phase> phases = new ArrayList<>();
        Range total = Range.ZERO;
        for (int i = 0; i < phaseItems.size(); i++) {
            Range sum = Range.ZERO;
            for (Item it : phaseItems.get(i)) {
                sum = sum.add(it.effort());
            }
            phases.add(new Phase(i, phaseNames.get(i), phaseItems.get(i), sum));
            total = total.add(sum);
        }

        // ---- modules left alone: cost shown, excluded from the total
        List<Excluded> excluded = new ArrayList<>();
        for (Score s : scores) {
            if (!s.decision().equals("LEAVE_ALONE") || phase1Modules.contains(s.module())) {
                continue;
            }
            ModuleFact m = facts.module(s.module());
            List<Item> items = new ArrayList<>();
            addModuleRows(model, options, m, s, whole(m), -1, items, step);
            Range sum = Range.ZERO;
            for (Item it : items) {
                sum = sum.add(it.effort());
            }
            excluded.add(new Excluded(s.module(), "Value " + s.businessValue() + " is at or below the leave-alone value "
                    + model.scoring().leaveAloneMaxValue() + "; the cost shown is what migrating it would take.", sum));
        }
        List<Score> finalScores = new ArrayList<>();
        for (Score s : scores) {
            Integer p = firstPhaseOf.get(s.module());
            finalScores.add(new Score(s.module(), s.businessValue(), s.couplingPerClass(), s.couplingBand(), s.riskSites(),
                    s.priority(), s.decision(), s.inM6Scope(), p, s.note()));
        }
        return new Result(finalScores, phases, excluded, total);
    }

    /**
     * The big-bang alternative: rebuild everything at once. Platform and harness, every module in full (leave-alone
     * modules included), every God class and every cycle edge. No gateway, no auth bridge and no Phase 0 fixes,
     * because nothing runs side by side and the old code is thrown away.
     */
    public static Range bigBang(Model model, Facts facts) {
        BigDecimal step = model.roundingStepDays();
        List<Score> scores = score(model, facts);
        Map<String, Score> byModule = new LinkedHashMap<>();
        scores.forEach(s -> byModule.put(s.module(), s));
        List<Item> items = new ArrayList<>();
        for (String k : List.of("platform", "characterizationHarness")) {
            Model.Rate r = model.fixed().get(k);
            items.add(item(1, Facts.PLATFORM, "FIXED", fixedLabel(k), "1", r.range(), BigDecimal.ONE, r.range(), step));
        }
        for (ModuleFact m : facts.modules()) {
            if (m.name().equals(Facts.PLATFORM)) {
                continue;
            }
            addModuleRows(model, Options.DEFAULT, m, byModule.get(m.name()), whole(m), 1, items, step);
            Model.Rate god = model.surcharges().get("godClass");
            for (String cls : m.godClasses()) {
                items.add(item(1, m.name(), "SURCHARGE", "Decompose God class " + simpleName(cls), "1", god.range(), BigDecimal.ONE,
                        god.range(), step));
            }
        }
        Model.Rate edge = model.surcharges().get("cycleEdge");
        for (int i = 0; i < facts.cycleEdges().size(); i++) {
            items.add(item(1, "All", "SURCHARGE", "Break one cycle dependency", "1", edge.range(), BigDecimal.ONE, edge.range(), step));
        }
        Range sum = Range.ZERO;
        for (Item it : items) {
            sum = sum.add(it.effort());
        }
        return sum;
    }

    // ------------------------------------------------------------------ scoring

    static List<Score> score(Model model, Facts facts) {
        List<Score> out = new ArrayList<>();
        for (ModuleFact m : facts.modules()) {
            if (m.name().equals(Facts.PLATFORM)) {
                continue;
            }
            Model.BusinessValue bv = model.businessValue().get(m.name());
            if (bv == null) {
                throw new IllegalArgumentException("estimation model: businessValue." + m.name() + " is missing");
            }
            BigDecimal coupling = m.classes() == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(m.couplingOut() + m.couplingIn()).divide(BigDecimal.valueOf(m.classes()), 2, RoundingMode.HALF_UP);
            String band = coupling.compareTo(model.scoring().couplingLowBelow()) < 0 ? "LOW"
                    : coupling.compareTo(model.scoring().couplingHighFrom()) >= 0 ? "HIGH" : "MEDIUM";
            boolean inScope = m.actions().stream().anyMatch(ActionFact::inScope);
            String decision = bv.value() <= model.scoring().leaveAloneMaxValue() ? "LEAVE_ALONE" : band.equals("LOW") ? "MIGRATE_FIRST" : "MIGRATE_LATER";
            String note = null;
            if (inScope && !decision.equals("MIGRATE_FIRST")) {
                note = "Only the in-scope actions move first (the M6 proof slice); the rest follows its score.";
            }
            if (inScope && decision.equals("LEAVE_ALONE")) {
                note = "Scope overrides: the module has in-scope actions, so it is not left alone.";
            }
            out.add(new Score(m.name(), bv.value(), coupling, band, m.securitySites(), bv.value() * (1 + m.securitySites()),
                    decision, inScope, null, note));
        }
        return out;
    }

    // ------------------------------------------------------------------ slices

    private static Slice whole(ModuleFact m) {
        return new Slice("whole", m.actions(), m.jsps(), true, distinctClasses(m.actions()));
    }

    private static Slice firstSlice(ModuleFact m) {
        List<ActionFact> scope = m.actions().stream().filter(ActionFact::inScope).toList();
        if (scope.size() == m.actions().size()) {
            return whole(m);
        }
        Set<String> files = new TreeSet<>();
        scope.forEach(a -> files.addAll(a.jspFiles()));
        List<JspFact> jsps = m.jsps().stream().filter(j -> files.contains(j.file())).toList();
        return new Slice("first slice", scope, jsps, true, distinctClasses(scope));
    }

    /** What is left after the first slice, or null when the module was not split. */
    private static Slice remainder(ModuleFact m) {
        boolean anyScope = m.actions().stream().anyMatch(ActionFact::inScope);
        if (!anyScope) {
            return whole(m);
        }
        List<ActionFact> rest = m.actions().stream().filter(a -> !a.inScope()).toList();
        if (rest.isEmpty()) {
            return null;
        }
        Slice first = firstSlice(m);
        Set<String> firstFiles = new TreeSet<>();
        first.jsps().forEach(j -> firstFiles.add(j.file()));
        List<JspFact> jsps = m.jsps().stream().filter(j -> !firstFiles.contains(j.file())).toList();
        Set<String> classes = distinctClasses(rest);
        classes.removeAll(first.classes());
        return new Slice("remainder", rest, jsps, false, classes);
    }

    private static Set<String> distinctClasses(List<ActionFact> actions) {
        Set<String> out = new LinkedHashSet<>();
        for (ActionFact a : actions) {
            if (!a.type().startsWith("org.apache.struts.")) {
                out.add(a.type());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ rows

    private static void addModuleRows(Model model, Options options, ModuleFact m, Score score, Slice slice, int phase,
            List<Item> out, BigDecimal step) {
        BigDecimal mult = multiplier(model, m, score);
        BigDecimal factor = BigDecimal.ONE.add(mult);
        int mappings = slice.actions().size();
        int forwards = (int) slice.actions().stream().filter(a -> a.kind().equals("ForwardAction")).count();
        int nonForward = mappings - forwards;
        int classes = options.chargeClassesPerMapping() ? nonForward : slice.classes().size();
        int blocks = slice.jsps().stream().mapToInt(JspFact::blocks).sum();

        row(out, phase, m.name(), "BUILD", options.chargeClassesPerMapping() ? "Action mappings charged as classes" : "Action classes",
                classes, model.rates().get("actionClass"), factor, step);
        row(out, phase, m.name(), "BUILD", "Mapping routing", mappings, model.rates().get("mappingRouting"), factor, step);
        row(out, phase, m.name(), "BUILD", "JSP templates", slice.jsps().size(), model.rates().get("jsp"), factor, step);
        row(out, phase, m.name(), "BUILD", "Scriptlet blocks", blocks, model.rates().get("scriptletBlock"), factor, step);
        if (slice.withDataLayer()) {
            row(out, phase, m.name(), "BUILD", "DAOs", m.daos(), model.rates().get("dao"), factor, step);
            row(out, phase, m.name(), "BUILD", "Managers", m.managers(), model.rates().get("manager"), factor, step);
            row(out, phase, m.name(), "BUILD", "Batch jobs", m.batchJobs(), model.rates().get("batchJob"), factor, step);
            row(out, phase, m.name(), "BUILD", "PDF reports", m.pdfReports(), model.rates().get("pdfReport"), factor, step);
        }
        row(out, phase, m.name(), "TEST", "Characterisation tests", nonForward, model.rates().get("characterizationTest"),
                BigDecimal.ONE, step);
    }

    private static void row(List<Item> out, int phase, String module, String kind, String what, int qty, Model.Rate rate,
            BigDecimal factor, BigDecimal step) {
        if (qty == 0) {
            return;
        }
        Range effort = rate.range().times(BigDecimal.valueOf(qty)).times(factor);
        out.add(item(phase, module, kind, what, String.valueOf(qty), rate.range(), factor, effort, step));
    }

    private static Item item(int phase, String module, String kind, String description, String qty, Range unit,
            BigDecimal factor, Range effort, BigDecimal step) {
        return new Item(phase, module, kind, description, qty, unit, factor.setScale(2, RoundingMode.HALF_UP), effort.roundedOutward(step));
    }

    /** Additive multiplier, capped. Applies to module build rows only. */
    static BigDecimal multiplier(Model model, ModuleFact m, Score score) {
        BigDecimal sum = BigDecimal.ZERO;
        Map<String, Model.Factor> f = model.multipliers().factors();
        if (m.inCycle()) {
            sum = sum.add(f.get("inCycle").value());
        }
        if (score != null && score.couplingBand().equals("MEDIUM")) {
            sum = sum.add(f.get("couplingMedium").value());
        }
        if (score != null && score.couplingBand().equals("HIGH")) {
            sum = sum.add(f.get("couplingHigh").value());
        }
        return sum.min(model.multipliers().cap());
    }

    private static void phase0(Model model, Facts facts, List<Item> out, BigDecimal step) {
        for (Map.Entry<String, Integer> e : facts.securitySitesByCategory().entrySet()) {
            Model.CategoryCost c = model.phase0().securityCategories().get(e.getKey());
            if (c == null) {
                throw new IllegalArgumentException("estimation model: phase0.securityCategories." + e.getKey() + " is missing");
            }
            String label = e.getKey().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
            if (c.base().high().signum() > 0) {
                out.add(item(0, "Legacy", "PHASE0", "Fix " + label + " (shared work)", "1", c.base().range(), BigDecimal.ONE,
                        c.base().range(), step));
            }
            if (c.perSite().high().signum() > 0 && e.getValue() > 0) {
                Range r = c.perSite().range().times(BigDecimal.valueOf(e.getValue()));
                out.add(item(0, "Legacy", "PHASE0", "Fix " + label + " (per site)", String.valueOf(e.getValue()),
                        c.perSite().range(), BigDecimal.ONE, r, step));
            }
        }
        List<String> noFix = new ArrayList<>();
        List<String> upgrade = new ArrayList<>();
        for (String family : facts.libraryFamilies()) {
            (model.phase0().noFixFamilies().contains(family) ? noFix : upgrade).add(family);
        }
        if (!upgrade.isEmpty()) {
            Model.Rate r = model.phase0().dependencyUpgradePerFamily();
            out.add(item(0, "Legacy", "PHASE0", "Upgrade or replace library families with advisories or end of life",
                    String.valueOf(upgrade.size()), r.range(), BigDecimal.ONE, r.range().times(BigDecimal.valueOf(upgrade.size())), step));
        }
        if (!noFix.isEmpty()) {
            Model.Rate r = model.phase0().compensatingControls();
            out.add(item(0, "Legacy", "PHASE0", "Compensating controls for frameworks with no fixed version",
                    String.valueOf(noFix.size()), r.range(), BigDecimal.ONE, r.range().times(BigDecimal.valueOf(noFix.size())), step));
        }
    }

    private static String fixedLabel(String key) {
        return switch (key) {
            case "platform" -> "Spring Boot 3 platform";
            case "gateway" -> "Gateway";
            case "authBridge" -> "Auth bridge";
            case "characterizationHarness" -> "Characterisation test harness";
            default -> key;
        };
    }

    private static String simpleName(String qualified) {
        return qualified.substring(qualified.lastIndexOf('.') + 1);
    }
}
