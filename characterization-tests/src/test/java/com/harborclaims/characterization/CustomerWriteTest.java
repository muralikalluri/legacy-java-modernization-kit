package com.harborclaims.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Create, update, validate and delete customers, always through the real forms and links. */
class CustomerWriteTest {

    private static final Session CSR = Run.as("csr1");

    @AfterAll
    static void cleanUp() {
        Customers.cleanup();
    }

    private static String repeat(char c, int n) {
        return String.valueOf(c).repeat(n);
    }

    private static List<String> idsOf(String lastName) {
        return Pages.customerIds(Pages.resultsPage(CSR.get("/customers/search.do?" + Session.query("q", lastName))));
    }

    // ---- create and update

    @Test
    void aValidCreateRedirectsToTheNewCustomersPage() {
        String last = Run.lastName("Create");
        Session.Reply r = Customers.save(CSR, Customers.valid(last));
        assertEquals(302, r.status());
        assertTrue(r.redirectPath().matches("/customers/view\\.do\\?id=\\d+"), r.location());
        Document view = CSR.get(r.redirectPath()).html();
        assertEquals("Test " + last, view.selectFirst("#customer-name").text());
        assertTrue(r.location() == null || !r.location().contains("jsessionid"));
        Customers.cleanup();
    }

    @Test
    void valuesAreTrimmedAndTheStateIsUpperCased() {
        String last = Run.lastName("Trim");
        Map<String, String> f = new LinkedHashMap<>(Customers.valid(last));
        f.put("firstName", "  Pat  ");
        f.put("email", "  " + Run.emailFor(last) + " ");
        f.put("state", "ny");
        f.put("phone", " 555-1234 ");
        f.put("dateOfBirth", "1990-01-02");
        f.put("city", "  Dunmere ");
        String id = Customers.createWith(f);
        Document edit = CSR.get("/customers/edit.do?id=" + id).html();
        assertEquals("Pat", edit.selectFirst("input[name=firstName]").attr("value"));
        assertEquals(Run.emailFor(last), edit.selectFirst("input[name=email]").attr("value"));
        assertEquals("NY", edit.selectFirst("input[name=state]").attr("value"));
        assertEquals("555-1234", edit.selectFirst("input[name=phone]").attr("value"));
        assertEquals("1990-01-02", edit.selectFirst("input[name=dateOfBirth]").attr("value"));
        assertEquals("Dunmere", edit.selectFirst("input[name=city]").attr("value"));
    }

    @Test
    void anUpdateKeepsTheSameIdAndShowsTheNewValue() {
        String last = Run.lastName("Upd");
        String id = Customers.create(last);
        Session.Reply r = Customers.update(CSR, id, Map.of("phone", "555-9999", "city", "Port Alder"));
        assertEquals(302, r.status());
        assertEquals("/customers/view.do?id=" + id, r.redirectPath());
        Document view = CSR.get("/customers/view.do?id=" + id).html();
        assertTrue(view.selectFirst("#customer-details").text().contains("555-9999"));
        assertTrue(view.selectFirst("#customer-details").text().contains("Port Alder"));
        assertEquals(List.of(id), idsOf(last), "an update does not create a second row");
    }

    @Test
    void markupAndQuotesInANameComeBackAsTextNotAsHtml() {
        String last = Run.lastName("Esc") + "<b>x</b>&\"";
        String id = Customers.createWith(Customers.valid(last).entrySet().stream().collect(LinkedHashMap::new,
                (m, e) -> m.put(e.getKey(), e.getKey().equals("email") ? Run.emailFor(Run.lastName("EscMail")) : e.getValue()), Map::putAll));
        Document view = CSR.get("/customers/view.do?id=" + id).html();
        assertNull(view.selectFirst("#customer-name b"), "the name must be escaped, not rendered as markup");
        assertEquals("Test " + last, view.selectFirst("#customer-name").text());
    }

    @Test
    void nonAsciiNamesRoundTrip() {
        String last = Run.lastName("Utf");
        Map<String, String> f = new LinkedHashMap<>(Customers.valid(last));
        f.put("firstName", "Zoë");
        String id = Customers.createWith(f);
        assertEquals("Zoë " + last, CSR.get("/customers/view.do?id=" + id).html().selectFirst("#customer-name").text());
    }

    // ---- validation

    static Stream<Arguments> invalidForms() {
        return Stream.of(
                Arguments.of("firstName", "", "firstName is required"),
                Arguments.of("firstName", "   ", "firstName is required"),
                Arguments.of("firstName", repeat('a', 51), "firstName must be at most 50 characters"),
                Arguments.of("lastName", repeat('b', 51), "lastName must be at most 50 characters"),
                Arguments.of("email", "", "email is required"),
                Arguments.of("email", "a@b", "Email address is not valid"),
                Arguments.of("email", "no-at-sign.example.test", "Email address is not valid"),
                Arguments.of("email", repeat('c', 115) + "@x.test", "Email address is not valid"),
                Arguments.of("phone", "abc", "Phone number is not valid"),
                Arguments.of("state", "ABC", "State must be two letters"),
                Arguments.of("state", "N", "State must be two letters"),
                Arguments.of("dateOfBirth", "1990-02-30", "Date of birth must be a past date in yyyy-MM-dd format"),
                Arguments.of("dateOfBirth", "1990-1-1", "Date of birth must be a past date in yyyy-MM-dd format"),
                Arguments.of("dateOfBirth", "01/02/1990", "Date of birth must be a past date in yyyy-MM-dd format"),
                Arguments.of("dateOfBirth", "2999-12-31", "Date of birth must be a past date in yyyy-MM-dd format"),
                Arguments.of("postalCode", repeat('9', 11), "postalCode must be at most 10 characters"),
                Arguments.of("id", "abc", "Invalid id"));
    }

    @ParameterizedTest
    @MethodSource("invalidForms")
    void anInvalidFieldGivesTheFormBackWithItsMessageAndStoresNothing(String field, String value, String message) {
        String last = Run.lastName("Bad");
        Map<String, String> f = new LinkedHashMap<>(Customers.valid(last));
        f.put(field, value);
        Session.Reply r = Customers.save(CSR, f);
        assertEquals(200, r.status(), "a validation failure is the form again, not a redirect");
        Document d = r.html();
        assertNotNull(d.selectFirst("#errors"), "the #errors hook is present");
        assertTrue(Pages.errors(d).contains(message), field + "=" + value + " gave " + Pages.errors(d));
        assertNotNull(d.selectFirst("form input[name=lastName]"), "the form is shown again");
        if (!field.equals("lastName")) {
            assertEquals(List.of(), idsOf(last), "nothing is stored for an invalid form");
        }
    }

    @Test
    void everyMissingRequiredFieldIsReportedAtOnce() {
        Session.Reply r = Customers.save(CSR, Map.of("firstName", "", "lastName", "", "email", ""));
        assertEquals(200, r.status());
        assertEquals(Set.of("firstName is required", "lastName is required", "email is required"), Pages.errors(r.html()));
    }

    @Test
    void rejectedValuesAreEchoedBackIntoTheForm() {
        String last = Run.lastName("Echo");
        Map<String, String> f = new LinkedHashMap<>(Customers.valid(last));
        f.put("email", "not-an-email");
        f.put("phone", "555-0000");
        Document d = Customers.save(CSR, f).html();
        assertEquals("not-an-email", d.selectFirst("input[name=email]").attr("value"));
        assertEquals("555-0000", d.selectFirst("input[name=phone]").attr("value"));
        assertEquals(last, d.selectFirst("input[name=lastName]").attr("value"));
    }

    // ---- duplicates and double submits

    @Test
    void anEmailInUseIsRejectedEvenInAnotherCaseOrPadded() {
        String last = Run.lastName("Dup");
        Customers.create(last);
        Map<String, String> again = new LinkedHashMap<>(Customers.valid(Run.lastName("Dup2")));
        again.put("email", "  " + Run.emailFor(last).toUpperCase() + " ");
        Session.Reply r = Customers.save(CSR, again);
        assertEquals(200, r.status());
        assertTrue(Pages.errors(r.html()).contains("That email address is already in use"), Pages.errors(r.html()).toString());
    }

    @Test
    void aCustomerMayKeepItsOwnEmailWhenUpdated() {
        String last = Run.lastName("Own");
        String id = Customers.create(last);
        Session.Reply r = Customers.update(CSR, id, Map.of("phone", "555-4321"));
        assertEquals(302, r.status());
    }

    @Test
    void submittingTheSameCreateTwiceStoresOneCustomerAndReportsTheDuplicate() {
        String last = Run.lastName("Twice");
        Map<String, String> f = Customers.valid(last);
        Session.Reply first = Customers.save(CSR, f);
        Session.Reply second = Customers.save(CSR, f);
        assertEquals(302, first.status());
        assertEquals(200, second.status());
        assertTrue(Pages.errors(second.html()).contains("That email address is already in use"));
        assertEquals(1, idsOf(last).size(), "exactly one row exists");
        Customers.cleanup();
    }

    @Test
    void savingAnUnknownIdIsNotFoundButADuplicateEmailIsReportedFirst() {
        String last = Run.lastName("Ghost");
        Session.Reply notFound = Customers.save(CSR, withId(Customers.valid(last), "99999"));
        assertEquals(404, notFound.status());
        String existing = Run.lastName("Taken");
        Customers.create(existing);
        Map<String, String> clash = withId(Customers.valid(Run.lastName("Ghost2")), "99999");
        clash.put("email", Run.emailFor(existing));
        Session.Reply dup = Customers.save(CSR, clash);
        assertEquals(200, dup.status());
        assertTrue(Pages.errors(dup.html()).contains("That email address is already in use"));
    }

    private static Map<String, String> withId(Map<String, String> m, String id) {
        Map<String, String> out = new LinkedHashMap<>(m);
        out.put("id", id);
        return out;
    }

    // ---- delete

    @Test
    void aCustomerWithPoliciesCannotBeDeletedAndStays() {
        Session.Reply r = Customers.delete(Run.as("admin"), "1");
        assertEquals(200, r.status(), "the page is shown again with the error");
        assertTrue(Pages.errors(r.html()).contains("Customer has policies and cannot be deleted"), Pages.errors(r.html()).toString());
        assertEquals("Amara Almeida", CSR.get("/customers/view.do?id=1").html().selectFirst("#customer-name").text());
    }

    @Test
    void deletingACustomerWithoutPoliciesRemovesItAndAsksNotFoundAfterwards() {
        String last = Run.lastName("Gone");
        String id = Customers.create(last);
        Session.Reply r = Customers.delete(Run.as("admin"), id);
        assertEquals(302, r.status());
        assertEquals("/customers/list.do", r.redirectPath());
        assertEquals(404, CSR.get("/customers/view.do?id=" + id).status());
        assertEquals(404, Customers.requestDelete(Run.as("admin"), id).status(), "a second delete finds nothing");
        assertEquals(List.of(), idsOf(last));
    }

    @Test
    void aCustomerWhoseOnlyPolicyIsCancelledStillCannotBeDeleted() {
        String id = Customers.create(Run.lastName("Canc"));
        Session.Reply policy = Forms.submit(Run.as("underwriter1"), "/policy/edit.do?customerId=" + id, "/policy/save",
                Map.of("customerId", id, "productType", "AUTO", "coverageLimit", "5000", "premium", "10.00", "startDate", "2025-02-01",
                        "endDate", "2026-01-31"));
        assertEquals(302, policy.status(), Pages.errors(policy.html()).toString());
        String policyId = policy.redirectPath().substring(policy.redirectPath().indexOf("id=") + 3);
        Session.Reply cancel = Forms.submit(Run.as("supervisor1"), "/policy/view.do?id=" + policyId, "/policy/cancel", Map.of());
        assertEquals(302, cancel.status());
        Session.Reply r = Customers.delete(Run.as("admin"), id);
        assertEquals(200, r.status());
        assertTrue(Pages.errors(r.html()).contains("Customer has policies and cannot be deleted"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"99999", "abc", "-1", "99999999999"})
    void editAndDeleteOfAnUnknownOrMalformedIdAreNotFound(String id) {
        assertEquals(404, Run.as("admin").get("/customers/edit.do?" + Session.query("id", id)).status(), "edit id=" + id);
        assertEquals(404, Customers.requestDelete(Run.as("admin"), id).status(), "delete id=" + id);
    }

    @Test
    void deleteWithoutAnIdIsNotFoundAndEditWithoutOneIsTheNewForm() {
        assertEquals(404, Customers.requestDelete(Run.as("admin"), null).status());
        Session.Reply r = Run.as("admin").get("/customers/edit.do");
        assertEquals(200, r.status());
        assertEquals("", r.html().selectFirst("input[name=id]").attr("value"));
    }
}
