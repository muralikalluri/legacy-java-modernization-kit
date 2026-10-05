package com.harborclaims.characterization;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Writes go through the form, the way a browser does: fetch the form, take every field it holds (hidden ones too, so an
 * anti-CSRF token is carried along if the target has one), override some, and submit to the form's own action.
 */
public final class Forms {

    private Forms() {
    }

    public static Element form(Document doc, String actionContains) {
        for (Element f : doc.select("form")) {
            if (f.attr("action").contains(actionContains)) {
                return f;
            }
        }
        throw new AssertionError("no form posting to " + actionContains + " on the page");
    }

    public static Map<String, String> fields(Element form) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Element in : form.select("input[name], textarea[name], select[name]")) {
            String type = in.attr("type").toLowerCase();
            if (type.equals("submit") || type.equals("button") || type.equals("image") || type.equals("reset")) {
                continue;
            }
            if ((type.equals("checkbox") || type.equals("radio")) && !in.hasAttr("checked")) {
                continue;
            }
            if (in.tagName().equals("select")) {
                Element selected = in.selectFirst("option[selected]");
                Element chosen = selected != null ? selected : in.selectFirst("option");
                out.put(in.attr("name"), chosen == null ? "" : chosen.hasAttr("value") ? chosen.attr("value") : chosen.text());
            } else if (in.tagName().equals("textarea")) {
                out.put(in.attr("name"), in.text());
            } else {
                out.put(in.attr("name"), in.attr("value"));
            }
        }
        return out;
    }

    /**
     * Hidden fields (such as an anti-CSRF token) of the first POST form on a page this session can load, or none. A session
     * may only use tokens from its own pages: a token taken from another user's session proves nothing about this one.
     */
    public static Map<String, String> hiddenFieldsOfAnyPostForm(Session session, String... pages) {
        for (String page : pages) {
            Session.Reply r = session.get(page);
            if (r.status() != 200) {
                continue;
            }
            for (Element f : r.html().select("form")) {
                if (f.attr("method").equalsIgnoreCase("post")) {
                    Map<String, String> out = new LinkedHashMap<>();
                    for (Element in : f.select("input[type=hidden][name]")) {
                        out.put(in.attr("name"), in.attr("value"));
                    }
                    return out;
                }
            }
        }
        return new LinkedHashMap<>();
    }

    /** Fetches the page, takes the form posting to {@code actionContains}, applies overrides and submits it. */
    public static Session.Reply submit(Session session, String pageUrl, String actionContains, Map<String, String> overrides) {
        Document doc = session.get(pageUrl).html();
        Element form = form(doc, actionContains);
        Map<String, String> data = fields(form);
        data.putAll(overrides);
        String action = form.attr("action");
        return session.post(action, data);
    }
}
