package com.harborclaims.assess.estimate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.harborclaims.assess.common.Json;
import com.harborclaims.assess.common.Modules;
import com.harborclaims.assess.estimate.EstimateEngine.Item;
import com.harborclaims.assess.estimate.EstimateEngine.Result;
import com.harborclaims.assess.estimate.Facts.ActionFact;
import com.harborclaims.assess.estimate.Facts.CycleEdge;
import com.harborclaims.assess.estimate.Facts.JspFact;
import com.harborclaims.assess.estimate.Facts.ModuleFact;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class EstimateEngineTest {

    private static final BigDecimal STEP = new BigDecimal("0.5");

    private static Model model() throws Exception {
        return Model.load(Path.of("estimation-model.yaml"));
    }

    // ---- synthetic facts

    private static ActionFact act(String path, String type, boolean inScope, String... jsps) {
        return new ActionFact(path, type, type.endsWith("ForwardAction") ? "ForwardAction" : "Action", inScope, List.of(jsps));
    }

    private static ModuleFact mod(String name, List<ActionFact> actions, List<JspFact> jsps, int classes, int out, int in, boolean cycle,
            List<String> gods) {
        return new ModuleFact(name, actions, jsps, 1, 1, 0, 0, classes, out, in, cycle, 0, gods);
    }

    private static ModuleFact empty(String name) {
        return new ModuleFact(name, List.of(), List.of(), 0, 0, 0, 0, 0, 0, 0, false, 0, List.of());
    }

    private static Facts facts(List<CycleEdge> edges, ModuleFact... mods) {
        List<ModuleFact> all = new ArrayList<>(List.of(mods));
        for (String n : List.of("Customers", "Claims", "Policies", "Reports", "Admin", "Platform")) {
            if (all.stream().noneMatch(m -> m.name().equals(n))) {
                all.add(empty(n));
            }
        }
        return new Facts(all, edges, Map.of(), List.of());
    }

    private static List<Item> items(Result r) {
        return r.phases().stream().flatMap(p -> p.items().stream()).toList();
    }

    private static Item find(Result r, int phase, String module, String description) {
        return items(r).stream().filter(i -> i.phase() == phase && i.module().equals(module) && i.description().equals(description))
                .findFirst().orElseThrow(() -> new AssertionError("no row " + phase + "/" + module + "/" + description));
    }

    // ---- invariants on synthetic facts

    @Test
    void aClassMappedTwiceIsChargedOnce() throws Exception {
        ModuleFact customers = mod("Customers", List.of(act("/customers/list", "demo.SearchAction", true, "a.jsp"),
                act("/customers/search", "demo.SearchAction", true, "a.jsp")), List.of(new JspFact("a.jsp", 0)), 1, 0, 0, false, List.of());
        Result r = EstimateEngine.estimate(model(), facts(List.of(), customers), EstimateEngine.Options.DEFAULT);
        assertEquals("1", find(r, 1, "Customers", "Action classes").quantity());
        assertEquals("2", find(r, 1, "Customers", "Mapping routing").quantity());
        Result perMapping = EstimateEngine.estimate(model(), facts(List.of(), customers), new EstimateEngine.Options(true));
        assertEquals("2", find(perMapping, 1, "Customers", "Action mappings charged as classes").quantity());
    }

    @Test
    void forwardActionsPayRoutingOnly() throws Exception {
        ModuleFact customers = mod("Customers", List.of(act("/customers/go", "org.apache.struts.actions.ForwardAction", true, "a.jsp")),
                List.of(), 0, 0, 0, false, List.of());
        Result r = EstimateEngine.estimate(model(), facts(List.of(), customers), EstimateEngine.Options.DEFAULT);
        assertTrue(items(r).stream().noneMatch(i -> i.description().equals("Action classes")));
        assertTrue(items(r).stream().noneMatch(i -> i.kind().equals("TEST")), "a plain forward needs no characterisation test");
        assertEquals("1", find(r, 1, "Customers", "Mapping routing").quantity());
    }

    private static Facts splitClaims(List<CycleEdge> edges, List<String> gods) {
        ModuleFact claims = mod("Claims", List.of(act("/claims/search", "demo.SearchAction", true, "s.jsp"),
                act("/claim/view", "demo.ViewAction", false, "v.jsp"), act("/claim/new", "demo.NewAction", false, "n.jsp")),
                List.of(new JspFact("s.jsp", 1), new JspFact("v.jsp", 2), new JspFact("n.jsp", 3)), 3, 4, 11, true, gods);
        ModuleFact policies = mod("Policies", List.of(act("/policy/view", "demo.PolicyViewAction", false, "p.jsp")),
                List.of(new JspFact("p.jsp", 0)), 1, 8, 12, true, List.of());
        ModuleFact customers = mod("Customers", List.of(act("/customers/list", "demo.CustomerAction", true, "c.jsp")),
                List.of(new JspFact("c.jsp", 0)), 1, 2, 2, false, List.of());
        return facts(edges, claims, policies, customers);
    }

    @Test
    void aSplitModuleAddsUpToTheWholeModule() throws Exception {
        Result r = EstimateEngine.estimate(model(), splitClaims(List.of(), List.of()), EstimateEngine.Options.DEFAULT);
        int phase2 = r.phases().stream().filter(p -> p.name().equals("Migrate the rest of Claims")).findFirst().orElseThrow().id();
        assertEquals(3, Integer.parseInt(find(r, 1, "Claims", "Action classes").quantity()) + Integer.parseInt(find(r, phase2, "Claims", "Action classes").quantity()));
        assertEquals(3, Integer.parseInt(find(r, 1, "Claims", "Mapping routing").quantity()) + Integer.parseInt(find(r, phase2, "Claims", "Mapping routing").quantity()));
        assertEquals(3, Integer.parseInt(find(r, 1, "Claims", "JSP templates").quantity()) + Integer.parseInt(find(r, phase2, "Claims", "JSP templates").quantity()));
        assertEquals(6, Integer.parseInt(find(r, 1, "Claims", "Scriptlet blocks").quantity()) + Integer.parseInt(find(r, phase2, "Claims", "Scriptlet blocks").quantity()));
        assertTrue(items(r).stream().noneMatch(i -> i.phase() == phase2 && i.description().equals("DAOs")),
                "the shared data layer is charged with the first slice only");
    }

    @Test
    void aGodClassIsChargedOnceEvenWhenItsModuleIsSplitAcrossPhases() throws Exception {
        Result r = EstimateEngine.estimate(model(), splitClaims(List.of(), List.of("demo.ClaimManager")), EstimateEngine.Options.DEFAULT);
        List<Item> gods = items(r).stream().filter(i -> i.description().startsWith("Decompose God class")).toList();
        assertEquals(1, gods.size());
        assertEquals(1, gods.get(0).phase(), "in the first phase that touches the module");
    }

    @Test
    void aCycleEdgeIsChargedOnceInTheFirstPhaseThatTouchesAnEndpoint() throws Exception {
        List<CycleEdge> edges = List.of(new CycleEdge("p.claims", "p.policies", "Claims", "Policies"),
                new CycleEdge("p.policies", "p.claims", "Policies", "Claims"),
                new CycleEdge("p.reports", "p.admin", "Reports", "Admin"));
        Result r = EstimateEngine.estimate(model(), splitClaims(edges, List.of()), EstimateEngine.Options.DEFAULT);
        List<Item> breaks = items(r).stream().filter(i -> i.description().startsWith("Break dependency")).toList();
        assertEquals(2, breaks.size(), "the Reports to Admin edge touches only modules left alone, so it is not charged");
        assertTrue(breaks.stream().allMatch(i -> i.phase() == 1), "Claims is touched in phase 1, so both edges are paid there, not again in Policies");
    }

    @Test
    void phaseOneIsExactlyTheModulesWithInScopeActions() throws Exception {
        Result r = EstimateEngine.estimate(model(), splitClaims(List.of(), List.of()), EstimateEngine.Options.DEFAULT);
        List<String> built = items(r).stream().filter(i -> i.phase() == 1 && i.kind().equals("BUILD")).map(Item::module).distinct().sorted().toList();
        assertEquals(List.of("Claims", "Customers"), built);
    }

    @Test
    void multipliersAddAndAreCappedAndTouchOnlyBuildRows() throws Exception {
        Model m = model();
        Model.Multipliers capped = new Model.Multipliers(new BigDecimal("0.10"), "test", m.multipliers().factors());
        Model low = new Model(m.unit(), m.roundingStepDays(), m.team(), m.rates(), capped, m.surcharges(), m.fixed(), m.phase0(), m.detect(),
                m.businessValue(), m.scoring());
        Result r = EstimateEngine.estimate(low, splitClaims(List.of(), List.of("demo.ClaimManager")), EstimateEngine.Options.DEFAULT);
        assertEquals(new BigDecimal("1.10"), find(r, 1, "Claims", "Action classes").multiplier(), "inCycle 0.15 + coupling is capped at 0.10");
        for (Item i : items(r)) {
            if (!i.kind().equals("BUILD")) {
                assertEquals(new BigDecimal("1.00"), i.multiplier(), i.kind() + " rows are never multiplied");
            }
        }
    }

    @Test
    void multipliersAreAdditiveNotChained() throws Exception {
        Model m = model();
        BigDecimal inCycle = m.multipliers().factors().get("inCycle").value();
        BigDecimal medium = m.multipliers().factors().get("couplingMedium").value();
        ModuleFact claims = mod("Claims", List.of(act("/claims/search", "demo.A", true)), List.of(), 1, 1, 1, true, List.of());
        EstimateEngine.Result r = EstimateEngine.estimate(m, facts(List.of(), claims), EstimateEngine.Options.DEFAULT);
        // coupling per class is (1 + 1) / 1 = 2.00, which is HIGH, so the factors are inCycle + couplingHigh
        BigDecimal expected = BigDecimal.ONE.add(inCycle).add(m.multipliers().factors().get("couplingHigh").value());
        assertEquals(expected.setScale(2), find(r, 1, "Claims", "Action classes").multiplier());
        BigDecimal chained = BigDecimal.ONE.add(inCycle).multiply(BigDecimal.ONE.add(m.multipliers().factors().get("couplingHigh").value()))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertNotEquals(chained, find(r, 1, "Claims", "Action classes").multiplier(), "1.15 x 1.15 would be 1.32; the model adds, so it is 1.30");
    }

    @Test
    void aModuleWithNothingInItGetsNoRowsAndNoError() throws Exception {
        Result r = EstimateEngine.estimate(model(), splitClaims(List.of(), List.of()), EstimateEngine.Options.DEFAULT);
        assertTrue(items(r).stream().noneMatch(i -> i.module().equals("Admin") || i.module().equals("Reports")));
        assertTrue(r.scores().stream().anyMatch(s -> s.module().equals("Admin")), "an empty module is still listed");
    }

    @Test
    void aMissingBusinessValueIsAHardError() throws Exception {
        Model m = model();
        Map<String, Model.BusinessValue> values = new TreeMap<>(m.businessValue());
        values.remove("Policies");
        Model broken = new Model(m.unit(), m.roundingStepDays(), m.team(), m.rates(), m.multipliers(), m.surcharges(), m.fixed(), m.phase0(),
                m.detect(), values, m.scoring());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> EstimateEngine.estimate(broken, splitClaims(List.of(), List.of()), EstimateEngine.Options.DEFAULT));
        assertTrue(e.getMessage().contains("businessValue.Policies"));
    }

    @Test
    void roundingGoesOutwardToTheStep() {
        assertEquals(new Range(new BigDecimal("0.0"), new BigDecimal("0.5")), new Range(new BigDecimal("0.25"), new BigDecimal("0.25")).roundedOutward(STEP));
        assertEquals(new Range(new BigDecimal("1.0"), new BigDecimal("3.0")), new Range(new BigDecimal("1.1"), new BigDecimal("2.6")).roundedOutward(STEP));
        assertEquals(new Range(new BigDecimal("1.0"), new BigDecimal("2.5")), new Range(new BigDecimal("1.0"), new BigDecimal("2.5")).roundedOutward(STEP));
    }

    @Test
    void bandsAddLowsAndHighsSeparately() {
        Range sum = new Range(new BigDecimal("1"), new BigDecimal("2")).add(new Range(new BigDecimal("3"), new BigDecimal("5")));
        assertEquals(new BigDecimal("4"), sum.low());
        assertEquals(new BigDecimal("7"), sum.high());
    }

    // ---- invariants on the real results

    private static Facts realFacts() throws Exception {
        Path r = Path.of("results");
        return Facts.build(Json.read(r.resolve("inventory.json")), Json.read(r.resolve("structure.json")), Json.read(r.resolve("security.json")),
                Json.read(r.resolve("deps.json")), Modules.load(Path.of("modules.yaml")), model());
    }

    @Test
    void totalsAreTheSumOfDisplayedRowsAndEveryRowIsALowHighBandOnTheStep() throws Exception {
        Result r = EstimateEngine.estimate(model(), realFacts(), EstimateEngine.Options.DEFAULT);
        BigDecimal low = BigDecimal.ZERO;
        BigDecimal high = BigDecimal.ZERO;
        for (EstimateEngine.Phase p : r.phases()) {
            BigDecimal pl = BigDecimal.ZERO;
            BigDecimal ph = BigDecimal.ZERO;
            for (Item i : p.items()) {
                assertTrue(i.effort().low().compareTo(i.effort().high()) <= 0, i.description());
                assertEquals(0, i.effort().low().remainder(STEP).signum(), "low is on the step: " + i.description());
                assertEquals(0, i.effort().high().remainder(STEP).signum(), "high is on the step: " + i.description());
                pl = pl.add(i.effort().low());
                ph = ph.add(i.effort().high());
            }
            assertEquals(0, pl.compareTo(p.total().low()), "phase " + p.id() + " low");
            assertEquals(0, ph.compareTo(p.total().high()), "phase " + p.id() + " high");
            low = low.add(p.total().low());
            high = high.add(p.total().high());
        }
        assertEquals(0, low.compareTo(r.total().low()));
        assertEquals(0, high.compareTo(r.total().high()));
        assertTrue(r.total().low().signum() > 0);
    }

    @Test
    void classAndMappingCountsAddUpToTheInventory() throws Exception {
        Facts f = realFacts();
        int mappings = 0;
        int classes = 0;
        for (ModuleFact m : f.modules()) {
            mappings += m.actions().size();
            classes += m.actions().stream().map(ActionFact::type).filter(t -> !t.startsWith("org.apache.struts.")).distinct().count();
        }
        assertEquals(35, mappings);
        assertEquals(31, classes, "a custom class belongs to exactly one module, so per-module distinct classes add up");
    }

    @Test
    void phaseOneIsTheMigrationScopeAndTheDecisionsMatchTheModel() throws Exception {
        Result r = EstimateEngine.estimate(model(), realFacts(), EstimateEngine.Options.DEFAULT);
        List<String> built = items(r).stream().filter(i -> i.phase() == 1 && i.kind().equals("BUILD")).map(Item::module).distinct().sorted().toList();
        assertEquals(List.of("Claims", "Customers"), built, "phase 1 builds exactly the modules in modules.yaml migrationScope");
        Map<String, String> decision = new TreeMap<>();
        r.scores().forEach(s -> decision.put(s.module(), s.decision()));
        assertEquals("MIGRATE_FIRST", decision.get("Customers"));
        assertEquals("MIGRATE_LATER", decision.get("Claims"));
        assertEquals("MIGRATE_LATER", decision.get("Policies"));
        assertEquals("LEAVE_ALONE", decision.get("Reports"));
        assertEquals("LEAVE_ALONE", decision.get("Admin"));
        assertTrue(r.scores().stream().filter(s -> s.module().equals("Claims")).findFirst().orElseThrow().note() != null,
                "the report must say Claims moves only as a slice");
        assertEquals(2, r.excluded().size());
    }

    @Test
    void phaseOneClaimsWorkIsExactlyTheClaimSearchAction() throws Exception {
        Result r = EstimateEngine.estimate(model(), realFacts(), EstimateEngine.Options.DEFAULT);
        assertEquals("1", find(r, 1, "Claims", "Action classes").quantity());
        assertEquals("1", find(r, 1, "Claims", "Mapping routing").quantity());
        assertEquals("1", find(r, 1, "Claims", "JSP templates").quantity());
        assertEquals("1", find(r, 1, "Claims", "Characterisation tests").quantity());
    }

    @Test
    void onlyModuleBuildRowsAreMultipliedOnTheRealData() throws Exception {
        Result r = EstimateEngine.estimate(model(), realFacts(), EstimateEngine.Options.DEFAULT);
        long phase0 = items(r).stream().filter(i -> i.phase() == 0).count();
        assertTrue(phase0 > 0, "the real data has Phase 0 rows to check");
        for (Item i : items(r)) {
            if (!i.kind().equals("BUILD")) {
                assertEquals(new BigDecimal("1.00"), i.multiplier(), i.kind() + " / " + i.description());
            }
        }
    }

    @Test
    void claimsSlicesAddUpOnTheRealData() throws Exception {
        Result r = EstimateEngine.estimate(model(), realFacts(), EstimateEngine.Options.DEFAULT);
        int phase2 = r.phases().stream().filter(p -> p.name().equals("Migrate the rest of Claims")).findFirst().orElseThrow().id();
        assertEquals(6, Integer.parseInt(find(r, 1, "Claims", "Action classes").quantity()) + Integer.parseInt(find(r, phase2, "Claims", "Action classes").quantity()));
        assertEquals(6, Integer.parseInt(find(r, 1, "Claims", "Mapping routing").quantity()) + Integer.parseInt(find(r, phase2, "Claims", "Mapping routing").quantity()));
        assertEquals(3, Integer.parseInt(find(r, 1, "Claims", "JSP templates").quantity()) + Integer.parseInt(find(r, phase2, "Claims", "JSP templates").quantity()));
    }

    @Test
    void theGodClassAndEachCycleEdgeAreChargedExactlyOnceOnTheRealData() throws Exception {
        Facts f = realFacts();
        Result r = EstimateEngine.estimate(model(), f, EstimateEngine.Options.DEFAULT);
        assertEquals(1, items(r).stream().filter(i -> i.description().startsWith("Decompose God class")).count());
        long edges = items(r).stream().filter(i -> i.description().startsWith("Break dependency")).count();
        assertEquals(f.cycleEdges().size(), edges, "every non-platform cycle edge touches a migrated module and is charged once");
    }

    @Test
    void everyParameterInTheModelChangesTheOutput() throws Exception {
        Facts f = realFacts();
        Model m = model();
        Result base = EstimateEngine.estimate(m, f, EstimateEngine.Options.DEFAULT);
        String baseKey = base.total() + "|" + base.excluded();
        for (String group : List.of("rates", "surcharges", "fixed")) {
            Map<String, Model.Rate> map = group.equals("rates") ? m.rates() : group.equals("surcharges") ? m.surcharges() : m.fixed();
            for (String key : map.keySet()) {
                Model scaled = EstimateBuilder.scaled(m, group, key, new BigDecimal("3"));
                Result r = EstimateEngine.estimate(scaled, f, EstimateEngine.Options.DEFAULT);
                assertNotEquals(baseKey, r.total() + "|" + r.excluded(), group + "." + key + " has no effect: a hidden constant or dead parameter");
            }
        }
    }

    @Test
    void sameInputsGiveByteIdenticalOutputAndTheTornadoIsSorted() throws Exception {
        Path r = Path.of("results");
        Map<String, Object> a = EstimateBuilder.build(Json.read(r.resolve("inventory.json")), Json.read(r.resolve("structure.json")),
                Json.read(r.resolve("security.json")), Json.read(r.resolve("deps.json")), Modules.load(Path.of("modules.yaml")), model(), "m", "x");
        Map<String, Object> b = EstimateBuilder.build(Json.read(r.resolve("inventory.json")), Json.read(r.resolve("structure.json")),
                Json.read(r.resolve("security.json")), Json.read(r.resolve("deps.json")), Modules.load(Path.of("modules.yaml")), model(), "m", "x");
        assertEquals(Json.toString(a), Json.toString(b));
        @SuppressWarnings("unchecked")
        List<EstimateBuilder.Sensitivity> sens = (List<EstimateBuilder.Sensitivity>) a.get("sensitivity");
        for (int i = 1; i < sens.size(); i++) {
            BigDecimal prev = sens.get(i - 1).halfEffectOnMidpoint().abs().max(sens.get(i - 1).oneAndHalfEffectOnMidpoint().abs());
            BigDecimal cur = sens.get(i).halfEffectOnMidpoint().abs().max(sens.get(i).oneAndHalfEffectOnMidpoint().abs());
            assertTrue(prev.compareTo(cur) >= 0);
        }
        assertFalse(sens.isEmpty());
    }
}
