package com.harborclaims.assess.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** XML parsing that never opens a URL: no external DTDs, no external entities. */
public final class Xml {

    /** Counts how many times the parser asked to resolve an external resource (it is always answered with nothing). */
    public static int resolverCalls;

    private Xml() {
    }

    public static Document parse(Path file) throws IOException {
        return parse(Files.readAllBytes(file));
    }

    public static Document parse(byte[] content) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            f.setValidating(false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setEntityResolver((publicId, systemId) -> {
                resolverCalls++;
                return new InputSource(new StringReader(""));
            });
            return b.parse(new ByteArrayInputStream(content));
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Cannot parse XML: " + e.getMessage(), e);
        }
    }

    /** Direct child elements with the given tag name, in document order. */
    public static List<Element> children(Element parent, String tag) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n instanceof Element e && e.getTagName().equals(tag)) {
                out.add(e);
            }
        }
        return out;
    }

    public static List<Element> descendants(Element parent, String tag) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) {
            out.add((Element) nodes.item(i));
        }
        return out;
    }

    public static String text(Element parent, String tag) {
        List<Element> kids = children(parent, tag);
        return kids.isEmpty() ? null : kids.get(0).getTextContent().trim();
    }
}
