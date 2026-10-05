package com.harborclaims.assess.jakarta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class JakartaTest {

    private static final String PATCH = String.join("\n",
            "diff --git a/src/main/java/demo/A.java b/src/main/java/demo/A.java",
            "index 111..222 100644",
            "--- a/src/main/java/demo/A.java",
            "+++ b/src/main/java/demo/A.java",
            "@@ -1 +1 @@",
            "-import javax.servlet.Filter;",
            "+import jakarta.servlet.Filter;",
            "diff --git a/src/main/java/demo/A.java b/src/main/java/demo/A.java",
            "diff --git a/pom.xml b/pom.xml",
            "diff --git a/src/test/java/demo/ATest.java b/src/test/java/demo/ATest.java",
            "diff --git a/src/main/webapp/WEB-INF/web.xml b/src/main/webapp/WEB-INF/web.xml",
            "diff --git a/src/old/Name.java b/src/main/java/demo/Name.java",
            "");

    @Test
    void parsesUniqueFilesAndRenames() {
        List<PatchParser.Change> c = PatchParser.parse(PATCH);
        assertEquals(5, c.size(), "the repeated A.java header counts once");
        assertEquals("src/old/Name.java", c.get(4).renamedFrom());
        assertNull(c.get(0).renamedFrom());
    }

    @Test
    void emptyAndMalformedPatchesMeanNoChanges() {
        assertEquals(0, PatchParser.parse("").size());
        assertEquals(0, PatchParser.parse(null).size());
        assertEquals(0, PatchParser.parse("this is not a diff\nat all").size());
    }

    @Test
    void filesAreClassifiedByKind() {
        assertEquals("mainJava", PatchParser.kindOf("src/main/java/demo/A.java"));
        assertEquals("testJava", PatchParser.kindOf("src/test/java/demo/ATest.java"));
        assertEquals("pom", PatchParser.kindOf("pom.xml"));
        assertEquals("xml", PatchParser.kindOf("src/main/webapp/WEB-INF/web.xml"));
        assertEquals("jsp", PatchParser.kindOf("src/main/webapp/WEB-INF/jsp/a.jsp"));
        assertEquals("other", PatchParser.kindOf("Dockerfile"));
    }

    @Test
    void aRunWithoutAResultIsAnErrorNotZeroChanges() throws Exception {
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                () -> RewriteRunner.requireResult("[INFO] BUILD SUCCESS", false));
        RewriteRunner.requireResult("[INFO] Applying recipes would make no changes. No patch file generated.", false);
        RewriteRunner.requireResult("[WARN] Report available", true);
    }

    @Test
    void theRewriteCommandPinsEveryVersionAndNeverUsesLatest() {
        List<String> cmd = RewriteRunner.command();
        String joined = String.join(" ", cmd);
        assertFalse(joined.contains("LATEST"));
        assertFalse(joined.contains("RELEASE"));
        assertTrue(joined.matches(".*rewrite-maven-plugin:\\d+\\.\\d+\\.\\d+:dryRun.*"));
        assertTrue(joined.matches(".*rewrite-migrate-java:\\d+\\.\\d+\\.\\d+.*"));
        assertTrue(joined.contains("JavaxMigrationToJakarta"));
        assertTrue(joined.contains(":dryRun"));
        assertFalse(joined.contains(":run "), "must never apply changes");
        assertTrue(cmd.stream().noneMatch(s -> s.contains("-q")), "quiet mode hides whether the recipe ran");
    }
}
