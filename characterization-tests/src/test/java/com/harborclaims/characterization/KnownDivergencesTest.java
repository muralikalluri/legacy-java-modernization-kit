package com.harborclaims.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * The CLOSED list of places where the migrated app is meant to behave differently from legacy, because legacy is wrong.
 * Each test pins a concrete expectation for BOTH targets, so a regression cannot hide here, and this is the only class
 * allowed to look at the target (see ContractGuardTest). Every divergence has a shared-behaviour test elsewhere on the
 * same route.
 */
class KnownDivergencesTest {

    /** The injection payload from the seeded flaw: a quote-closing tautology that survives the % wildcards. */
    private static final String INJECTION = "zzz%') OR 1=1 OR 'a' LIKE lower('%";

    @AfterAll
    static void cleanUp() {
        Customers.cleanup();
    }

    @Test
    void theListOfDivergencesIsClosed() {
        long divergences = java.util.Arrays.stream(KnownDivergencesTest.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Test.class) && m.getName().startsWith("divergence")).count();
        long allTests = java.util.Arrays.stream(KnownDivergencesTest.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(Test.class)).count();
        assertEquals(7, divergences, "adding or removing a divergence must be a deliberate, reviewed change");
        assertEquals(8, allTests, "every test in this class is either a divergence or this check: nothing else may hide here");
    }

    private static Document results(String path) {
        return Pages.resultsPage(Run.as("csr1").get(path));
    }

    @Test
    void divergence1_customerSearchTermIsInjectableInLegacyButNotInModern() {
        Document d = results("/customers/search.do?" + Session.query("q", INJECTION));
        assertEquals(Target.isLegacy() ? 10 : 0, Pages.customerIds(d).size());
    }

    @Test
    void divergence2_claimSearchTermIsInjectableInLegacyButNotInModern() {
        Document d = Pages.resultsPage(Run.as("auditor1").get("/claims/search.do?" + Session.query("q", INJECTION)));
        assertEquals(Target.isLegacy() ? 10 : 0, Pages.claimIds(d).size());
    }

    @Test
    void divergence3_claimStatusIsInjectableInLegacyButNotInModern() {
        Document d = Pages.resultsPage(Run.as("auditor1").get("/claims/search.do?" + Session.query("status", "PAID' OR '1'='1")));
        if (Target.isLegacy()) {
            assertTrue(Pages.claimStatuses(d).stream().anyMatch(s -> !s.equals("PAID")), "rows of other statuses leak through");
        } else {
            assertEquals(List.of(), Pages.claimIds(d));
        }
    }

    @Test
    void divergence4_anApostropheInACustomerSearchCrashesLegacyButWorksInModern() {
        Session.Reply r = Run.as("csr1").get("/customers/search.do?" + Session.query("q", "O'Brien"));
        if (Target.isLegacy()) {
            assertEquals(500, r.status());
        } else {
            assertEquals(200, r.status());
            assertNotNull(r.html().selectFirst("#results"));
            assertEquals(List.of(), Pages.customerIds(r.html()));
        }
    }

    @Test
    void divergence5_anApostropheInAClaimSearchCrashesLegacyButWorksInModern() {
        for (String query : List.of(Session.query("q", "O'Brien"), Session.query("status", "PAID'"))) {
            Session.Reply r = Run.as("auditor1").get("/claims/search.do?" + query);
            if (Target.isLegacy()) {
                assertEquals(500, r.status(), query);
            } else {
                assertEquals(200, r.status(), query);
                assertNotNull(r.html().selectFirst("#results"), query);
                assertEquals(List.of(), Pages.claimIds(r.html()), query);
            }
        }
    }

    @Test
    void divergence6_aPostWithoutAnAntiCsrfTokenSucceedsInLegacyAndIsRefusedInModern() {
        String last = Run.lastName("NoTok");
        Session.Reply r = Run.as("csr1").post("/customers/save.do", Map.of("id", "", "firstName", "Test", "lastName", last,
                "email", Run.emailFor(last)));
        List<String> found = Pages.customerIds(Pages.resultsPage(Run.as("csr1").get("/customers/search.do?" + Session.query("q", last))));
        if (Target.isLegacy()) {
            assertEquals(302, r.status());
            assertEquals(1, found.size());
        } else {
            assertEquals(403, r.status());
            assertEquals(List.of(), found, "nothing is created without a token");
        }
    }

    @Test
    void divergence7_deletingWithAPlainGetWorksInLegacyAndIsRefusedInModern() {
        String id = Customers.create(Run.lastName("GetDel"));
        Session.Reply r = Run.as("admin").get("/customers/delete.do?id=" + id);
        int after = Run.as("csr1").get("/customers/view.do?id=" + id).status();
        if (Target.isLegacy()) {
            assertEquals(302, r.status());
            assertEquals(404, after, "the customer is gone");
        } else {
            assertTrue(r.status() == 403 || r.status() == 405, "a state-changing GET is refused: " + r.status());
            assertEquals(200, after, "and the customer survives");
        }
    }
}
