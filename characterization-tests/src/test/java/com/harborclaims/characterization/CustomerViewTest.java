package com.harborclaims.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerViewTest {

    @AfterAll
    static void cleanUp() {
        Customers.cleanup();
    }

    @Test
    void aSeededCustomerPageShowsItsDetailsAndPolicies() {
        Session.Reply r = Run.as("auditor1").get("/customers/view.do?id=1");
        assertEquals(200, r.status());
        Document d = r.html();
        assertEquals("Amara Almeida", d.selectFirst("#customer-name").text());
        Element details = d.selectFirst("#customer-details");
        assertEquals("1", details.attr("data-customer-id"));
        String text = details.text();
        assertTrue(text.contains("amara.almeida@example.test"), text);
        assertTrue(text.contains("555-0101"), text);
        assertTrue(text.contains("1961-02-01"), text);
        assertTrue(text.contains("107 Harbor Lane"), text);
    }

    @Test
    void thePolicyTableListsThisCustomersPoliciesInIdOrder() {
        Document d = Run.as("auditor1").get("/customers/view.do?id=1").html();
        List<String> numbers = d.select("#policies tr").stream().skip(1).map(tr -> tr.select("td").first().text()).toList();
        assertEquals(List.of("POL-000001", "POL-000031"), numbers);
        assertTrue(d.selectFirst("#policies").text().contains("ACTIVE"));
    }

    @Test
    void aCustomerWithNoPoliciesHasAnEmptyPolicyTable() {
        String id = Customers.create(Run.lastName("View"));
        Document d = Run.as("csr1").get("/customers/view.do?id=" + id).html();
        assertNotNull(d.selectFirst("#policies"));
        assertEquals(0, d.select("#policies tr").size() - 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"99999", "abc", "-1", "99999999999", "1.5", ""})
    void anUnknownOrMalformedIdIsNotFoundNotABadRequest(String id) {
        assertEquals(404, Run.as("csr1").get("/customers/view.do?" + Session.query("id", id)).status(), "id=" + id);
    }

    @Test
    void aMissingIdIsNotFound() {
        assertEquals(404, Run.as("csr1").get("/customers/view.do").status());
    }

    @Test
    void editAndDeleteLinksAppearOnlyForTheRolesThatMayUseThem() {
        for (String user : Roles.ALL) {
            Document d = Run.as(user).get("/customers/view.do?id=1").html();
            boolean canEdit = Roles.CUSTOMER_WRITERS.contains(user);
            assertEquals(canEdit, d.selectFirst("#edit-link") != null, user + " edit link");
            assertEquals(user.equals("admin"), d.selectFirst("#delete-link") != null, user + " delete link");
        }
        Document admin = Run.as("admin").get("/customers/view.do?id=1").html();
        assertEquals("/customers/edit.do?id=1", admin.selectFirst("#edit-link").attr("href"));
        assertEquals("/customers/delete.do?id=1", admin.selectFirst("#delete-link").attr("href"));
        assertNull(Run.as("auditor1").get("/customers/view.do?id=1").html().selectFirst("#delete-link"));
    }
}
