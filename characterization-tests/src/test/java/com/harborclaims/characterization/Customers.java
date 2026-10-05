package com.harborclaims.characterization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Shared flows for creating and removing customers through the real forms and links. */
public final class Customers {

    /** Ids of customers created by this run, so a class can clean up after itself (best effort). */
    private static final List<String> CREATED = new ArrayList<>();

    private Customers() {
    }

    public static synchronized void cleanup() {
        for (String id : new ArrayList<>(CREATED)) {
            tryDelete(id);
        }
        CREATED.clear();
    }

    public static Map<String, String> valid(String lastName) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("firstName", "Test");
        m.put("lastName", lastName);
        m.put("email", Run.emailFor(lastName));
        return m;
    }

    /** Submits the new-customer form as the given user. */
    public static Session.Reply save(Session who, Map<String, String> overrides) {
        return Forms.submit(who, "/customers/edit.do", "/customers/save", overrides);
    }

    /** Edits an existing customer through its own edit form. */
    public static Session.Reply update(Session who, String id, Map<String, String> overrides) {
        return Forms.submit(who, "/customers/edit.do?id=" + id, "/customers/save", overrides);
    }

    /** Creates a customer and returns its id, failing if the create did not redirect to the view page. */
    public static String create(String lastName) {
        return createWith(Customers.valid(lastName));
    }

    public static String createWith(Map<String, String> fields) {
        Session.Reply r = save(Run.as("csr1"), fields);
        if (r.status() != 302 || !r.redirectPath().startsWith("/customers/view.do?id=")) {
            throw new AssertionError("create failed: HTTP " + r.status() + " " + r.location() + " " + Pages.errors(r.html()));
        }
        String id = r.redirectPath().substring("/customers/view.do?id=".length());
        synchronized (Customers.class) {
            CREATED.add(id);
        }
        return id;
    }

    /**
     * Follows the page's own delete control, as a person would, whether it is a link or a form button.
     * Returns the reply of the delete request.
     */
    public static Session.Reply delete(Session who, String id) {
        Document view = who.get("/customers/view.do?id=" + id).html();
        Element control = view.selectFirst("#delete-link");
        if (control == null) {
            throw new AssertionError("no #delete-link on the customer page");
        }
        Element form = control.tagName().equals("form") ? control : control.closest("form");
        if (form != null) {
            Map<String, String> data = Forms.fields(form);
            data.put("id", id);
            return who.post(form.attr("action"), data);
        }
        return who.get(control.attr("href"));
    }

    /**
     * Sends a delete request for ANY id (or none), as the given user, using the delete mechanism the page offers an
     * administrator: a link means a GET to its path, a form means a POST to its action. Tokens come from the acting
     * session's own pages. Used for the role, 404 and malformed-id cases, where there may be no page to follow.
     */
    public static Session.Reply requestDelete(Session who, String idOrNull) {
        Element control = Run.as("admin").get("/customers/view.do?id=1").html().selectFirst("#delete-link");
        if (control == null) {
            throw new AssertionError("an administrator's customer page has no #delete-link");
        }
        Element form = control.tagName().equals("form") ? control : control.closest("form");
        if (form == null) {
            String href = control.attr("href");
            String path = href.contains("?") ? href.substring(0, href.indexOf('?')) : href;
            return who.get(idOrNull == null ? path : path + "?" + Session.query("id", idOrNull));
        }
        Map<String, String> data = Forms.fields(form);
        data.remove("id");
        data.putAll(Forms.hiddenFieldsOfAnyPostForm(who, "/customers/search.do", "/claims/search.do", "/customers/list.do"));
        if (idOrNull != null) {
            data.put("id", idOrNull);
        }
        return who.post(form.attr("action"), data);
    }

    /** Best-effort cleanup used after a test class. */
    public static void tryDelete(String id) {
        try {
            if (Run.as("admin").get("/customers/view.do?id=" + id).status() == 200) {
                delete(Run.as("admin"), id);
            }
        } catch (RuntimeException e) {
            System.out.println("cleanup of customer " + id + " skipped: " + e.getMessage());
        }
    }
}
