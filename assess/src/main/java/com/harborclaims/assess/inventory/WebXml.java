package com.harborclaims.assess.inventory;

import com.harborclaims.assess.common.Xml;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** web.xml facts. Filter order is the order of the filter-mapping elements, which is meaningful. */
public final class WebXml {

    public record Filter(String name, String className, List<String> urlPatterns) {
    }

    public record Servlet(String name, String className, List<String> urlPatterns) {
    }

    public record ErrorPage(String code, String location) {
    }

    public record ResourceRef(String name, String type) {
    }

    public record Parsed(String namespace, String version, List<Filter> filtersInOrder, List<Servlet> servlets,
            List<String> listeners, List<ErrorPage> errorPages, List<ResourceRef> resourceRefs,
            String sessionTimeoutMinutes, List<String> trackingModes) {
    }

    private WebXml() {
    }

    public static Parsed parse(Path file) throws IOException {
        return parse(Xml.parse(file));
    }

    public static Parsed parse(Document doc) {
        Element root = doc.getDocumentElement();
        Map<String, String> filterClasses = new LinkedHashMap<>();
        for (Element f : Xml.children(root, "filter")) {
            filterClasses.put(Xml.text(f, "filter-name"), Xml.text(f, "filter-class"));
        }
        Map<String, List<String>> filterPatterns = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        for (Element m : Xml.children(root, "filter-mapping")) {
            String name = Xml.text(m, "filter-name");
            if (!order.contains(name)) {
                order.add(name);
            }
            String pattern = Xml.text(m, "url-pattern");
            if (pattern != null) {
                filterPatterns.computeIfAbsent(name, k -> new ArrayList<>()).add(pattern);
            }
        }
        List<Filter> filters = new ArrayList<>();
        for (String name : order) {
            filters.add(new Filter(name, filterClasses.get(name), filterPatterns.getOrDefault(name, List.of())));
        }

        Map<String, List<String>> servletPatterns = new LinkedHashMap<>();
        for (Element m : Xml.children(root, "servlet-mapping")) {
            servletPatterns.computeIfAbsent(Xml.text(m, "servlet-name"), k -> new ArrayList<>()).add(Xml.text(m, "url-pattern"));
        }
        List<Servlet> servlets = new ArrayList<>();
        for (Element s : Xml.children(root, "servlet")) {
            String name = Xml.text(s, "servlet-name");
            servlets.add(new Servlet(name, Xml.text(s, "servlet-class"), servletPatterns.getOrDefault(name, List.of())));
        }
        List<String> listeners = new ArrayList<>();
        for (Element l : Xml.children(root, "listener")) {
            listeners.add(Xml.text(l, "listener-class"));
        }
        List<ErrorPage> errorPages = new ArrayList<>();
        for (Element e : Xml.children(root, "error-page")) {
            String code = Xml.text(e, "error-code");
            errorPages.add(new ErrorPage(code == null ? Xml.text(e, "exception-type") : code, Xml.text(e, "location")));
        }
        List<ResourceRef> refs = new ArrayList<>();
        for (Element r : Xml.children(root, "resource-ref")) {
            refs.add(new ResourceRef(Xml.text(r, "res-ref-name"), Xml.text(r, "res-type")));
        }
        String timeout = null;
        List<String> tracking = new ArrayList<>();
        for (Element sc : Xml.children(root, "session-config")) {
            timeout = Xml.text(sc, "session-timeout");
            for (Element t : Xml.children(sc, "tracking-mode")) {
                tracking.add(t.getTextContent().trim());
            }
        }
        return new Parsed(root.getAttribute("xmlns"), root.getAttribute("version"), filters, servlets, listeners,
                errorPages, refs, timeout, tracking);
    }
}
