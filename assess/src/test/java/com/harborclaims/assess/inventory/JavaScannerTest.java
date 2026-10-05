package com.harborclaims.assess.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class JavaScannerTest {

    private static final String SOURCE = String.join("\n",
            "package demo.web;",
            "",
            "import javax.servlet.http.HttpServletRequest;",
            "import javax.servlet.*;",
            "import static javax.servlet.http.HttpServletResponse.SC_OK;",
            "import javax.sql.DataSource;",
            "import java.util.List;",
            "",
            "// import javax.mail.Session; (a comment)",
            "/* javax.persistence.Entity in a block comment */",
            "public class Demo {",
            "    String s = \"javax.ws.rs.Path in a string\";",
            "    char c = '\\'';",
            "    javax.servlet.ServletContext ctx;",
            "    void m() { javax.naming.Context x = null; }",
            "}");

    @Test
    void importsAreReadFromCodeOnly() {
        JavaScanner.Result r = JavaScanner.scan(SOURCE);
        assertEquals("demo.web", r.pkg());
        assertEquals("Demo", r.className());
        assertEquals(List.of("javax.servlet.http", "javax.servlet", "javax.servlet.http", "javax.sql"), r.javaxImports());
    }

    @Test
    void fullyQualifiedUsesInBodiesAreCountedButStringsAndCommentsAreNot() {
        JavaScanner.Result r = JavaScanner.scan(SOURCE);
        assertEquals(List.of("javax.servlet", "javax.naming"), r.javaxQualified());
    }

    @Test
    void bucketsSeparateJakartaMigratingFromJdkOwned() {
        assertEquals(Javax.JAKARTA, Javax.bucket("javax.servlet.http"));
        assertEquals(Javax.JAKARTA, Javax.bucket("javax.annotation"));
        assertEquals(Javax.JAKARTA, Javax.bucket("javax.xml.bind"));
        assertEquals(Javax.JDK, Javax.bucket("javax.annotation.processing"));
        assertEquals(Javax.JDK, Javax.bucket("javax.sql"));
        assertEquals(Javax.JDK, Javax.bucket("javax.naming"));
        assertEquals(Javax.JDK, Javax.bucket("javax.xml.parsers"));
        assertEquals(Javax.JDK, Javax.bucket("javax.transaction.xa"));
        assertEquals(Javax.JAKARTA, Javax.bucket("javax.transaction"));
        assertEquals(Javax.OTHER, Javax.bucket("javax.something.else"));
    }

    @Test
    void packageOfStopsAtTheFirstTypeName() {
        assertEquals("javax.servlet.http", Javax.packageOf("javax.servlet.http.HttpServletRequest"));
        assertEquals("javax.servlet.http", Javax.packageOf("javax.servlet.http.HttpServletResponse.SC_OK"));
    }

    @Test
    void linesAreCounted() {
        assertEquals(16, JavaScanner.scan(SOURCE).lines());
    }
}
