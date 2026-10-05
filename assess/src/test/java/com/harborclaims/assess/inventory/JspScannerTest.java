package com.harborclaims.assess.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JspScannerTest {

    @Test
    void countsEveryKindAndEveryOccurrenceNotEveryLine() {
        String jsp = String.join("\n",
                "<%@ page import=\"javax.servlet.http.HttpSession\" %>",
                "<%@ taglib uri=\"http://struts.apache.org/tags-html\" prefix=\"html\" %>",
                "<% int a = 1; %> <% int b = 2; %> <% int c = 3; %>",
                "<%= a %> and <%= b %>",
                "<%! int shared = 0; %>",
                "<jsp:scriptlet>int d = 4;</jsp:scriptlet>",
                "<jsp:expression>d</jsp:expression>",
                "<jsp:declaration>int e;</jsp:declaration>",
                "<!-- <% int inHtmlComment = 1; %> -->",
                "${el} is not a scriptlet");
        JspScanner.Result r = JspScanner.scan(jsp);
        assertEquals(5, r.scriptlets(), "three on one line, one in an HTML comment, one <jsp:scriptlet>");
        assertEquals(3, r.expressions(), "two <%= and one <jsp:expression>");
        assertEquals(2, r.declarations(), "one <%! and one <jsp:declaration>");
    }

    @Test
    void jspCommentsHidingScriptletsAreIgnoredEvenAcrossLines() {
        String jsp = "<%-- <% hidden(); %>\n still comment <%= also %> --%>\n<% real(); %>";
        JspScanner.Result r = JspScanner.scan(jsp);
        assertEquals(1, r.scriptlets());
        assertEquals(0, r.expressions());
    }

    @Test
    void escapedOpenersAndDirectivesAreNotScriptlets() {
        JspScanner.Result r = JspScanner.scan("<\\% literal %> <%@ page contentType=\"text/html\" %>");
        assertEquals(0, r.totalScriptletBlocks());
    }

    @Test
    void javaxReferencesAndJstlUrisAreCountedSeparately() {
        JspScanner.Result r = JspScanner.scan("<%@ page import=\"javax.servlet.http.HttpSession\" %>\n"
                + "<%@ taglib uri=\"http://java.sun.com/jsp/jstl/core\" prefix=\"c\" %>\n"
                + "<%@ taglib uri=\"http://struts.apache.org/tags-html\" prefix=\"html\" %>");
        assertEquals(1, r.javaxByPackage().get("javax.servlet.http"));
        assertEquals(1, r.jstlUris());
    }

    @Test
    void linesAreCounted() {
        assertEquals(3, JspScanner.scan("a\nb\nc").lines());
        assertEquals(2, JspScanner.scan("a\nb\n").lines());
        assertEquals(0, JspScanner.scan("").lines());
    }
}
