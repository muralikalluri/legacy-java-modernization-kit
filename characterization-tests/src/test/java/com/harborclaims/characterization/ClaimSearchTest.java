package com.harborclaims.characterization;

import static com.harborclaims.characterization.Pages.claimIds;
import static com.harborclaims.characterization.Pages.resultsPage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Claims search. Seeded claims are read-only: assertions are exact values and ordered prefixes, never totals. */
class ClaimSearchTest {

    private static final Session AUDITOR = Run.as("auditor1");

    @AfterAll
    static void cleanUp() {
        Customers.cleanup();
    }

    static List<String> allRoles() {
        return Roles.ALL;
    }

    private static Document search(String q, String status, String page) {
        return resultsPage(AUDITOR.get("/claims/search.do?" + Session.query("q", q, "status", status, "page", page)));
    }

    private static Document search(String q, String status) {
        return search(q, status, "");
    }

    private static List<String> range(int from, int to) {
        List<String> out = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            out.add(String.valueOf(i));
        }
        return out;
    }

    // ---- access

    @ParameterizedTest
    @MethodSource("allRoles")
    void everySignedInRoleCanSearchClaims(String user) {
        Document d = resultsPage(Run.as(user).get("/claims/search.do"));
        assertEquals(10, claimIds(d).size());
    }

    @Test
    void withoutASessionTheSearchRedirectsToTheLoginPage() {
        Session.Reply r = Session.anonymous().get("/claims/search.do?q=CLM-000007");
        assertEquals(302, r.status());
        assertEquals("/login.do", r.redirectPath());
    }

    // ---- what the term matches

    @Test
    void aClaimNumberFindsThatClaim() {
        Document d = search("CLM-000007", "");
        assertEquals(List.of("7"), claimIds(d));
        assertEquals("UNDER_REVIEW", Pages.claimStatuses(d).get(0));
        assertEquals(1, Pages.total(d));
    }

    @Test
    void theClaimNumberMatchIsCaseInsensitive() {
        assertEquals(List.of("7"), claimIds(search("clm-000007", "")));
        assertEquals(claimIds(search("CLM-000007", "")), claimIds(search("Clm-000007", "")));
    }

    @Test
    void aClaimNumberPrefixFindsTheNumbersThatContainIt() {
        Document d = search("CLM-00000", "");
        assertEquals(range(1, 9), claimIds(d), "ordered by id");
        assertEquals(9, Pages.total(d));
    }

    @Test
    void aCustomersLastNameFindsTheirClaimsOrderedById() {
        List<String> ids = claimIds(search("almeida", ""));
        List<String> expectedPrefix = List.of("1", "11", "21", "31", "37", "47", "57");
        assertTrue(ids.subList(0, Math.min(ids.size(), expectedPrefix.size())).equals(expectedPrefix.subList(0, Math.min(ids.size(), expectedPrefix.size())))
                && ids.size() >= expectedPrefix.size(), "claims of the three Almeida customers, by id: " + ids);
        assertEquals(claimIds(search("almeida", "")), claimIds(search("ALMEIDA", "")), "last name search is case-insensitive");
        assertEquals(claimIds(search("almeida", "")), claimIds(search("  almeida ", "")), "and trimmed");
    }

    @Test
    void onlyTheClaimNumberAndTheCustomersLastNameAreSearched() {
        for (String term : List.of("Amara", "POL-000001", "example.test", "water leak")) {
            Document d = search(term, "");
            assertEquals(List.of(), claimIds(d), "'" + term + "' must not match anything");
            assertEquals(0, Pages.total(d));
        }
    }

    @Test
    void underscoreAndPercentActAsSqlWildcards() {
        assertEquals(List.of("1", "7"), claimIds(search("CLM-00000_", "UNDER_REVIEW")), "_ matches any single character");
        assertEquals(claimIds(search("", "")), claimIds(search("%", "")), "a lone % matches everything");
    }

    // ---- status filter

    @Test
    void aStatusFilterKeepsOnlyThatStatus() {
        Document d = search("CLM-00000", "UNDER_REVIEW");
        assertEquals(List.of("1", "7"), claimIds(d));
        assertEquals(List.of("UNDER_REVIEW", "UNDER_REVIEW"), Pages.claimStatuses(d));
        Document paid = search("", "PAID");
        assertTrue(Pages.claimStatuses(paid).stream().allMatch("PAID"::equals));
        assertEquals(10, claimIds(paid).size());
    }

    @Test
    void theStatusValueIsTrimmedButNotCaseFolded() {
        assertEquals(claimIds(search("", "PAID")), claimIds(search("", "  PAID ")));
        Document lower = search("", "paid");
        assertEquals(List.of(), claimIds(lower), "'paid' is not 'PAID': no rows");
        assertEquals(0, Pages.total(lower));
    }

    @Test
    void anUnknownStatusFindsNothingAndAnEmptyOneMeansAny() {
        assertEquals(List.of(), claimIds(search("", "NOPE")));
        assertEquals(claimIds(search("", "")), range(1, 10));
    }

    // ---- order and paging

    @Test
    void claimsAreOrderedByIdAndPagesHoldTen() {
        assertEquals(range(1, 10), claimIds(search("", "", "1")));
        assertEquals(range(11, 20), claimIds(search("", "", "2")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-3", "abc", "1.5", "99999999999"})
    void pageValuesThatAreNotAPositiveWholeNumberMeanPageOne(String value) {
        assertEquals(range(1, 10), claimIds(search("", "", value)), "page=" + value);
    }

    @ParameterizedTest
    @ValueSource(strings = {" 2 ", "+2"})
    void paddedAndSignedPageNumbersAreAccepted(String value) {
        assertEquals(range(11, 20), claimIds(search("", "", value)), "page=" + value);
    }

    @Test
    void aPagePastTheEndIsAnEmptyResultsPage() {
        Document d = search("CLM-00000", "", "99");
        assertEquals(List.of(), claimIds(d));
        assertNull(Pages.pagerParams(d, "next"));
    }

    @Test
    void pagerLinksCarryTheTermAndStatusInDecodedForm() {
        Document first = search("a%", "", "1");
        Map<String, String> next = Pages.pagerParams(first, "next");
        assertNotNull(next, "more than one page of claims match a%");
        assertEquals("a%", next.get("q"));
        assertEquals("", next.get("status"));
        assertEquals("2", next.get("page"));
        Document filtered = search("", "PAID", "1");
        assertEquals("PAID", filtered.select("#results [data-claim-id]").first().attr("data-status"));
        Document second = search("", "", "2");
        assertEquals("1", Pages.pagerParams(second, "prev").get("page"));
        assertEquals("/claims/search.do", Pages.path(second.selectFirst("#pager #prev").attr("href")));
    }

    // ---- hooks

    @Test
    void thePageCarriesTheHooksTheContractNames() {
        Document d = search("CLM-000007", "UNDER_REVIEW");
        assertNotNull(d.selectFirst("#results"));
        assertNotNull(d.selectFirst("#total"));
        assertNotNull(d.selectFirst("#pager"));
        assertNotNull(d.selectFirst("form[action$='/claims/search.do'] input[name=q]"));
        assertEquals("CLM-000007", d.selectFirst("form[action$='/claims/search.do'] input[name=q]").attr("value"));
        assertEquals("UNDER_REVIEW", d.selectFirst("form[action$='/claims/search.do'] select[name=status] option[selected]").attr("value"));
        assertEquals("/claim/view.do?id=7", d.selectFirst("#results [data-claim-id=7] a").attr("href"));
    }

    // ---- fixtures built through the routes that stay on legacy

    @Test
    void claimsOfAFreshCustomerAreFoundByLastNameInIdOrderAndFilterByStatus() {
        String last = Run.lastName("Clm");
        String customerId = Customers.create(last);
        Session.Reply policy = Forms.submit(Run.as("underwriter1"), "/policy/edit.do?customerId=" + customerId, "/policy/save",
                Map.of("customerId", customerId, "productType", "HOME", "coverageLimit", "5000", "premium", "10.00",
                        "startDate", "2025-02-01", "endDate", "2026-01-31"));
        assertEquals(302, policy.status(), Pages.errors(policy.html()).toString());
        String policyNumber = Run.as("underwriter1").get(policy.redirectPath()).html().selectFirst("#policy-number").text();

        String first = newClaim(policyNumber, "100.00");
        String second = newClaim(policyNumber, "200.00");
        Session.Reply move = Forms.submit(Run.as("adjuster1"), "/claim/view.do?id=" + second, "/claim/transition", Map.of("toStatus", "UNDER_REVIEW"));
        assertEquals(302, move.status());

        Document all = search(last, "");
        assertEquals(List.of(first, second), claimIds(all), "ascending by id");
        assertEquals(2, Pages.total(all));
        assertEquals(List.of(first), claimIds(search(last, "NEW")));
        assertEquals(List.of(second), claimIds(search(last, "UNDER_REVIEW")));
        assertEquals(List.of(), claimIds(search(last, "PAID")));
    }

    @Test
    void theStatusFilterIsCarriedInThePagerLinks() {
        String last = Run.lastName("Pgr");
        String customerId = Customers.create(last);
        Session.Reply policy = Forms.submit(Run.as("underwriter1"), "/policy/edit.do?customerId=" + customerId, "/policy/save",
                Map.of("customerId", customerId, "productType", "HOME", "coverageLimit", "5000", "premium", "10.00",
                        "startDate", "2025-02-01", "endDate", "2026-01-31"));
        assertEquals(302, policy.status());
        String policyNumber = Run.as("underwriter1").get(policy.redirectPath()).html().selectFirst("#policy-number").text();
        for (int i = 0; i < 11; i++) {
            newClaim(policyNumber, "10." + String.format("%02d", i));
        }
        Document first = search(last, "NEW", "1");
        assertEquals(10, claimIds(first).size());
        Map<String, String> next = Pages.pagerParams(first, "next");
        assertNotNull(next, "eleven claims need a second page");
        assertEquals("NEW", next.get("status"), "the status filter travels in the pager link");
        assertEquals(last, next.get("q"));
        assertEquals("2", next.get("page"));
        Document second = search(last, "NEW", "2");
        assertEquals(1, claimIds(second).size());
        assertEquals("NEW", Pages.pagerParams(second, "prev").get("status"));
        assertEquals(11, Pages.total(first));
    }

    private static String newClaim(String policyNumber, String amount) {
        Session.Reply r = Forms.submit(Run.as("adjuster1"), "/claim/new.do", "/claim/save",
                Map.of("policyNumber", policyNumber, "lossDate", "2025-06-01", "amountClaimed", amount, "description", "Characterisation fixture"));
        assertEquals(302, r.status(), Pages.errors(r.html()).toString());
        return r.redirectPath().substring(r.redirectPath().indexOf("id=") + 3);
    }
}
