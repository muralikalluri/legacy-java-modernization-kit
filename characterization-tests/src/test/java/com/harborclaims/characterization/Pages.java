package com.harborclaims.characterization;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Reads the stable hooks that make up the contract between the pages and these tests. Never layout. */
public final class Pages {

    private Pages() {
    }

    /** Row ids in the results table. Scoped to #results because other tables on a page carry the same attributes. */
    public static List<String> customerIds(Document d) {
        return attrs(d, "#results [data-customer-id]", "data-customer-id");
    }

    public static List<String> claimIds(Document d) {
        return attrs(d, "#results [data-claim-id]", "data-claim-id");
    }

    public static List<String> claimStatuses(Document d) {
        return attrs(d, "#results [data-claim-id]", "data-status");
    }

    private static List<String> attrs(Document d, String selector, String attr) {
        List<String> out = new ArrayList<>();
        for (Element e : d.select(selector)) {
            out.add(e.attr(attr));
        }
        return out;
    }

    /** The "Last, First" cell of each customer row. */
    public static List<String> customerNames(Document d) {
        List<String> out = new ArrayList<>();
        for (Element row : d.select("#results [data-customer-id]")) {
            out.add(row.selectFirst("td a").text());
        }
        return out;
    }

    /** Asserts the page really is a results page (a login page or an error page also has no rows) and returns it. */
    public static Document resultsPage(Session.Reply r) {
        if (r.status() != 200) {
            throw new AssertionError("expected a results page but got HTTP " + r.status() + " " + r.location());
        }
        Document d = r.html();
        if (d.selectFirst("#results") == null || d.selectFirst("#total") == null) {
            throw new AssertionError("page has no #results table and #total: not a results page");
        }
        return d;
    }

    public static int total(Document d) {
        return Integer.parseInt(d.selectFirst("#total").text().trim());
    }

    /** The messages of #errors as a set: order is not part of the contract. */
    public static Set<String> errors(Document d) {
        Set<String> out = new LinkedHashSet<>();
        for (Element li : d.select("#errors li")) {
            out.add(li.text().trim());
        }
        return out;
    }

    /** Decoded query parameters of a pager link, so "+" and "%20" are the same space. */
    public static Map<String, String> pagerParams(Document d, String id) {
        Element a = d.selectFirst("#pager #" + id);
        if (a == null) {
            return null;
        }
        String href = a.attr("href");
        String query = href.contains("?") ? href.substring(href.indexOf('?') + 1) : "";
        Map<String, String> out = new java.util.TreeMap<>();
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            String[] kv = pair.split("=", 2);
            out.put(java.net.URLDecoder.decode(kv[0], java.nio.charset.StandardCharsets.UTF_8),
                    kv.length > 1 ? java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8) : "");
        }
        return out;
    }

    public static String path(String href) {
        return href.contains("?") ? href.substring(0, href.indexOf('?')) : href;
    }
}
