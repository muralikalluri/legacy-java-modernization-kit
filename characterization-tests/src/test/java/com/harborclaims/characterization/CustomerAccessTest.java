package com.harborclaims.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Who may do what, for every role. The role check runs before the existence check and before validation. */
class CustomerAccessTest {

    @AfterAll
    static void cleanUp() {
        Customers.cleanup();
    }

    static List<String> allRoles() {
        return Roles.ALL;
    }

    static List<String> readOnlyRoles() {
        return Roles.CUSTOMER_READ_ONLY;
    }

    static List<String> writerRoles() {
        return Roles.CUSTOMER_WRITERS;
    }

    @ParameterizedTest
    @MethodSource("allRoles")
    void everySignedInRoleCanReadCustomers(String user) {
        Session s = Run.as(user);
        assertEquals(10, Pages.customerIds(Pages.resultsPage(s.get("/customers/list.do"))).size());
        assertEquals(List.of("1", "21", "11"), Pages.customerIds(Pages.resultsPage(s.get("/customers/search.do?q=almeida"))));
        assertEquals("Amara Almeida", s.get("/customers/view.do?id=1").html().selectFirst("#customer-name").text());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/customers/list.do", "/customers/search.do?q=almeida", "/customers/view.do?id=1", "/customers/edit.do",
            "/customers/edit.do?id=1", "/claims/search.do"})
    void withoutASessionEveryPageRedirectsToTheLoginPage(String path) {
        Session.Reply r = Session.anonymous().get(path);
        assertEquals(302, r.status(), path);
        assertEquals("/login.do", r.redirectPath(), "no return-to parameter: " + path);
        assertTrue(r.location() == null || !r.location().contains("jsessionid"));
    }

    @Test
    void anonymousPostsAreRedirectedToo() {
        Session.Reply r = Session.anonymous().post("/customers/save.do", Map.of("firstName", "x", "lastName", "y", "email", "z@z.zz"));
        assertEquals(302, r.status());
        assertEquals("/login.do", r.redirectPath());
    }

    @ParameterizedTest
    @MethodSource("writerRoles")
    void writersCanOpenTheEditForms(String user) {
        assertEquals(200, Run.as(user).get("/customers/edit.do").status());
        assertEquals(200, Run.as(user).get("/customers/edit.do?id=1").status());
    }

    @ParameterizedTest
    @MethodSource("readOnlyRoles")
    void otherRolesAreForbiddenTheEditForms(String user) {
        assertEquals(403, Run.as(user).get("/customers/edit.do").status());
        assertEquals(403, Run.as(user).get("/customers/edit.do?id=1").status());
    }

    @ParameterizedTest
    @MethodSource("writerRoles")
    void writersCanSave(String user) {
        String last = Run.lastName("Save");
        Session.Reply r = Customers.save(Run.as(user), Customers.valid(last));
        assertEquals(302, r.status());
        assertTrue(r.redirectPath().startsWith("/customers/view.do?id="), r.location());
        String id = r.redirectPath().substring("/customers/view.do?id=".length());
        assertEquals(List.of(id), Pages.customerIds(Pages.resultsPage(Run.as("csr1").get("/customers/search.do?" + Session.query("q", last)))));
    }

    @ParameterizedTest
    @MethodSource("readOnlyRoles")
    void forbiddenRolesCannotSaveAndNothingIsStored(String user) {
        String last = Run.lastName("Deny");
        Element form = Forms.form(Run.as("csr1").get("/customers/edit.do").html(), "/customers/save");
        Map<String, String> data = Forms.fields(form);
        data.putAll(Customers.valid(last));
        // a token (if the target uses one) must come from this user's own pages, never from another user's session
        data.putAll(Forms.hiddenFieldsOfAnyPostForm(Run.as(user), "/customers/search.do", "/claims/search.do"));
        Session.Reply r = Run.as(user).post(form.attr("action"), data);
        assertEquals(403, r.status());
        Document found = Pages.resultsPage(Run.as("csr1").get("/customers/search.do?" + Session.query("q", last)));
        assertEquals(List.of(), Pages.customerIds(found), "a forbidden save must not change anything");
    }

    @Test
    void onlyAnAdministratorCanDelete() {
        for (String user : List.of("csr1", "supervisor1", "adjuster1", "underwriter1", "auditor1")) {
            String id = Customers.create(Run.lastName("Del"));
            assertEquals(403, Customers.requestDelete(Run.as(user), id).status(), user);
            assertEquals(200, Run.as("csr1").get("/customers/view.do?id=" + id).status(), "the customer survives a forbidden delete");
        }
        String id = Customers.create(Run.lastName("Del"));
        Session.Reply r = Customers.delete(Run.as("admin"), id);
        assertEquals(302, r.status());
        assertEquals("/customers/list.do", r.redirectPath());
    }

    @Test
    void theRoleCheckComesBeforeTheExistenceCheckAndBeforeValidation() {
        assertEquals(403, Run.as("auditor1").get("/customers/edit.do?id=99999").status(), "forbidden, not not-found");
        assertEquals(403, Customers.requestDelete(Run.as("csr1"), "99999").status());
        Session.Reply invalid = Run.as("auditor1").post("/customers/save.do", Map.of("firstName", "", "lastName", "", "email", "bad"));
        assertEquals(403, invalid.status(), "an invalid form from a forbidden role is still forbidden");
    }

    @Test
    void afterLoggingOutThePagesRedirectAgain() {
        Session s = Session.login("csr1");
        assertEquals(200, s.get("/customers/list.do").status());
        Session.Reply out = s.get("/logout.do");
        assertEquals(302, out.status());
        assertEquals("/login.do", out.redirectPath());
        Session.Reply after = s.get("/customers/list.do");
        assertEquals(302, after.status());
        assertEquals("/login.do", after.redirectPath());
    }
}
