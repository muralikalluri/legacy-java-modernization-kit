package com.harborclaims.characterization;

import static com.harborclaims.characterization.Pages.customerIds;
import static com.harborclaims.characterization.Pages.resultsPage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Customer search and listing. Seeded rows are read-only, so assertions are exact values and ordered prefixes, never totals. */
class CustomerSearchTest {

    private static final Session CSR = Run.as("csr1");

    @AfterAll
    static void cleanUp() {
        Customers.cleanup();
    }

    private static Document search(String q) {
        return resultsPage(CSR.get("/customers/search.do?" + Session.query("q", q)));
    }

    private static Document page(String q, String page) {
        return resultsPage(CSR.get("/customers/search.do?" + Session.query("q", q, "page", page)));
    }

    @Test
    void searchingALastNameReturnsItsCustomersOrderedByLastNameThenFirstName() {
        Document d = search("almeida");
        assertEquals(List.of("1", "21", "11"), customerIds(d), "Amara, Ines, Katya: first name breaks the tie, not the id");
        assertEquals(List.of("Almeida, Amara", "Almeida, Ines", "Almeida, Katya"), Pages.customerNames(d));
        assertEquals(3, Pages.total(d));
    }

    @Test
    void searchIsCaseInsensitive() {
        assertEquals(customerIds(search("almeida")), customerIds(search("ALMEIDA")));
        assertEquals(customerIds(search("almeida")), customerIds(search("AlMeIdA")));
        assertFalse(customerIds(search("ALMEIDA")).isEmpty());
    }

    @Test
    void theQueryIsTrimmed() {
        assertEquals(customerIds(search("almeida")), customerIds(search("  almeida ")));
    }

    @Test
    void aBlankQueryIsTheSameAsAnEmptyQuery() {
        assertEquals(customerIds(search("")), customerIds(search("   ")));
        assertEquals(10, customerIds(search("")).size());
    }

    @Test
    void listAndSearchFilterIdentically() {
        Document list = resultsPage(CSR.get("/customers/list.do?" + Session.query("q", "almeida")));
        assertEquals(customerIds(search("almeida")), customerIds(list));
        assertEquals(10, customerIds(resultsPage(CSR.get("/customers/list.do"))).size());
    }

    @Test
    void emailAndFirstNameAreSearchedToo() {
        assertEquals(List.of("1"), customerIds(search("amara.almeida@")));
        List<String> amaras = customerIds(search("amara"));
        assertTrue(amaras.containsAll(List.of("1", "13", "25")), "every seeded Amara: " + amaras);
    }

    @Test
    void eachFieldIsMatchedOnItsOwnSoAFullNameFindsNothing() {
        Document d = search("Amara Almeida");
        assertEquals(List.of(), customerIds(d));
        assertEquals(0, Pages.total(d));
    }

    @Test
    void anUnknownTermIsAnEmptyResultsPageNotAnError() {
        Document d = search("zzzznothing");
        assertEquals(List.of(), customerIds(d));
        assertEquals(0, Pages.total(d));
    }

    @Test
    void underscoreAndPercentActAsSqlWildcards() {
        assertEquals(List.of("1", "21", "11"), customerIds(search("almeid_")));
        assertEquals(List.of("1", "21", "11"), customerIds(search("almei%a")));
        assertEquals(customerIds(search("")), customerIds(search("%")), "a lone % matches everything");
    }

    @Test
    void theUnfilteredOrderIsLastNameFirstNameThenId() {
        assertEquals(List.of("1", "21", "11", "2", "22", "12", "13", "3", "23", "14"), customerIds(search("")),
                "Almeida (3), Brandt (3), Castellan (3), then Dumont, Bruno");
    }

    @Test
    void pagesHoldTenRowsAreDisjointAndContinueTheOrder() {
        Document p1 = page("", "1");
        Document p2 = page("", "2");
        assertEquals(10, customerIds(p1).size());
        assertEquals(10, customerIds(p2).size());
        List<String> both = new ArrayList<>(customerIds(p1));
        both.addAll(customerIds(p2));
        assertEquals(20, both.stream().distinct().count(), "no row appears on two pages");
        List<String> names = new ArrayList<>(Pages.customerNames(p1));
        names.addAll(Pages.customerNames(p2));
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String::compareTo);
        assertEquals(sorted, names, "the ASCII names stay in order across the page boundary");
    }

    @Test
    void identicalNamesAreOrderedByIdSoPagingIsStable() {
        String last = Run.lastName("Tie");
        String first = Customers.createWith(withEmail(last, "one"));
        String second = Customers.createWith(withEmail(last, "two"));
        List<String> ids = customerIds(search(last));
        assertEquals(List.of(first, second), ids, "same first and last name: the lower id comes first");
        assertEquals(customerIds(search(last)), customerIds(search(last)), "two identical calls give an identical order");
    }

    private static Map<String, String> withEmail(String lastName, String label) {
        Map<String, String> m = new LinkedHashMap<>(Customers.valid(lastName));
        m.put("email", label + "." + Run.emailFor(lastName));
        return m;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-3", "abc", "1.5", "99999999999", "%20"})
    void pageValuesThatAreNotAPositiveWholeNumberMeanPageOne(String value) {
        assertEquals(customerIds(page("", "1")), customerIds(page("", value)), "page=" + value);
    }

    @ParameterizedTest
    @ValueSource(strings = {" 2 ", "+2"})
    void paddedAndSignedPageNumbersAreAccepted(String value) {
        assertEquals(customerIds(page("", "2")), customerIds(page("", value)), "page=" + value);
    }

    @Test
    void aPagePastTheEndIsAnEmptyResultsPage() {
        Document d = page("almeida", "99");
        assertEquals(List.of(), customerIds(d));
        assertNull(Pages.pagerParams(d, "next"));
    }

    @Test
    void pagerLinksCarryTheSearchTermAndPageInDecodedForm() {
        Document first = page("a%", "1");
        assertNull(Pages.pagerParams(first, "prev"), "no previous link on page one");
        Map<String, String> next = Pages.pagerParams(first, "next");
        assertEquals("a%", next.get("q"));
        assertEquals("2", next.get("page"));
        Document second = page("a%", "2");
        assertEquals("1", Pages.pagerParams(second, "prev").get("page"));
        assertEquals("a%", Pages.pagerParams(second, "prev").get("q"));
        assertEquals("/customers/search.do", Pages.path(second.selectFirst("#pager #prev").attr("href")));
    }

    @Test
    void thePageCarriesTheHooksTheContractNames() {
        Document d = search("almeida");
        assertNotNull(d.selectFirst("#results"));
        assertNotNull(d.selectFirst("#total"));
        assertNotNull(d.selectFirst("#pager"));
        assertNotNull(d.selectFirst("form[action$='/customers/search.do'] input[name=q]"));
        assertEquals("almeida", d.selectFirst("form[action$='/customers/search.do'] input[name=q]").attr("value"));
        assertEquals("/customers/view.do?id=1", d.selectFirst("#results [data-customer-id=1] a").attr("href"));
    }

    private static void assertNotNull(Object o) {
        assertTrue(o != null, "missing hook");
    }
}
