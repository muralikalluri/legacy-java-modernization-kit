package com.harborclaims.assess.common;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Element;

public final class PomReader {

    public record Dependency(String groupId, String artifactId, String version, String scope) {

        public String coordinates() {
            return groupId + ":" + artifactId;
        }
    }

    private PomReader() {
    }

    /** Direct dependencies in file order. The scope defaults to "compile". */
    public static List<Dependency> dependencies(Path pom) throws IOException {
        Element root = Xml.parse(pom).getDocumentElement();
        List<Dependency> out = new ArrayList<>();
        for (Element deps : Xml.children(root, "dependencies")) {
            for (Element d : Xml.children(deps, "dependency")) {
                String scope = Xml.text(d, "scope");
                out.add(new Dependency(Xml.text(d, "groupId"), Xml.text(d, "artifactId"), Xml.text(d, "version"),
                        scope == null ? "compile" : scope));
            }
        }
        return out;
    }
}
