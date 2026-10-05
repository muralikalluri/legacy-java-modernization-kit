package com.harborclaims.assess.inventory;

import com.harborclaims.assess.common.Xml;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Tiles definitions, with the body JSP resolved through "extends". */
public final class TilesDefs {

    public record Definition(String name, String parent, String layout, String ownBody) {
    }

    private final Map<String, Definition> byName = new LinkedHashMap<>();

    public static TilesDefs parse(Path file) throws IOException {
        return parse(Xml.parse(file));
    }

    public static TilesDefs parse(Document doc) {
        TilesDefs t = new TilesDefs();
        for (Element d : Xml.descendants(doc.getDocumentElement(), "definition")) {
            String body = null;
            for (Element p : Xml.children(d, "put")) {
                if ("body".equals(p.getAttribute("name"))) {
                    body = p.getAttribute("value");
                }
            }
            t.byName.put(d.getAttribute("name"), new Definition(d.getAttribute("name"),
                    d.hasAttribute("extends") ? d.getAttribute("extends") : null,
                    d.hasAttribute("path") ? d.getAttribute("path") : null, body));
        }
        return t;
    }

    public Map<String, Definition> definitions() {
        return byName;
    }

    public boolean has(String name) {
        return byName.containsKey(name);
    }

    /** The body JSP of a definition, following extends. Null when the definition or a body cannot be found. */
    public String resolveBody(String name) {
        String current = name;
        for (int hops = 0; hops < 20 && current != null; hops++) {
            Definition d = byName.get(current);
            if (d == null) {
                return null;
            }
            if (d.ownBody() != null && !d.ownBody().isEmpty()) {
                return d.ownBody();
            }
            current = d.parent();
        }
        return null;
    }
}
