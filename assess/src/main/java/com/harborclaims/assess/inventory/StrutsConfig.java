package com.harborclaims.assess.inventory;

import com.harborclaims.assess.common.Xml;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** The parts of struts-config.xml the assessment needs. */
public final class StrutsConfig {

    public record Forward(String name, String path, boolean redirect) {
    }

    public record Action(String path, String type, String form, String scope, String validate, String input,
            String parameter, List<String> rolesDeclared, List<Forward> forwards) {
    }

    public record FormBean(String name, String type) {
    }

    public record Parsed(List<FormBean> formBeans, List<Action> actions, String processorClass, List<String> plugIns) {
    }

    private StrutsConfig() {
    }

    public static Parsed parse(Path file) throws IOException {
        return parse(Xml.parse(file));
    }

    public static Parsed parse(Document doc) {
        Element root = doc.getDocumentElement();
        List<FormBean> beans = new ArrayList<>();
        for (Element fbs : Xml.children(root, "form-beans")) {
            for (Element fb : Xml.children(fbs, "form-bean")) {
                beans.add(new FormBean(fb.getAttribute("name"), fb.getAttribute("type")));
            }
        }
        List<Action> actions = new ArrayList<>();
        for (Element ams : Xml.children(root, "action-mappings")) {
            for (Element a : Xml.children(ams, "action")) {
                List<Forward> forwards = new ArrayList<>();
                for (Element f : Xml.children(a, "forward")) {
                    forwards.add(new Forward(f.getAttribute("name"), f.getAttribute("path"),
                            "true".equals(f.getAttribute("redirect"))));
                }
                actions.add(new Action(a.getAttribute("path"), a.getAttribute("type"), empty(a, "name"), empty(a, "scope"),
                        empty(a, "validate"), empty(a, "input"), empty(a, "parameter"), roles(a), forwards));
            }
        }
        String processor = null;
        for (Element c : Xml.children(root, "controller")) {
            processor = empty(c, "processorClass");
        }
        List<String> plugIns = new ArrayList<>();
        for (Element p : Xml.children(root, "plug-in")) {
            plugIns.add(p.getAttribute("className"));
        }
        return new Parsed(beans, actions, processor, plugIns);
    }

    private static String empty(Element e, String attr) {
        return e.hasAttribute(attr) ? e.getAttribute(attr) : null;
    }

    /** null when the attribute is absent, otherwise the trimmed, non-empty role names (possibly an empty list). */
    static List<String> roles(Element action) {
        if (!action.hasAttribute("roles")) {
            return null;
        }
        return Arrays.stream(action.getAttribute("roles").split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
