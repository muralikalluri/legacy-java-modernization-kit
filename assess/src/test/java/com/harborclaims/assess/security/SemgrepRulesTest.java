package com.harborclaims.assess.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Runs the real ruleset with Semgrep. Skipped when Semgrep is not installed; CI installs a pinned version. */
class SemgrepRulesTest {

    private static final Path RULES = Path.of("rules");

    private static void needSemgrep() {
        Assumptions.assumeTrue(SemgrepRunner.available(), "semgrep is not installed");
    }

    private static Map<String, Integer> counts(SemgrepRunner.Result r) {
        Map<String, Integer> m = new TreeMap<>();
        r.findings().forEach(f -> m.merge(f.ruleId(), 1, Integer::sum));
        return m;
    }

    @Test
    void javaRulesHitOnlyTheIntendedFixtureSites() throws Exception {
        needSemgrep();
        SemgrepRunner.Result r = SemgrepRunner.run(RULES, Path.of("src/test/resources/semgrep/Fixture.java"), "src/test/resources/semgrep/");
        assertEquals(0, r.errors());
        assertEquals(1, counts(r).get("harbor.sql-concat-taint"), "only the tainted concatenation, not constants, COLS or prepared SQL");
        assertEquals(1, counts(r).get("harbor.prepared-statement-concat"), "only the concatenated non-constant, not literals joined across lines");
        assertEquals(1, counts(r).get("harbor.plaintext-password-compare"), "getPassword().equals only, not other equals calls");
    }

    @Test
    void jspRulesCoverPlainMultilineDefaultMethodAndUppercaseVariantsButNotGetOrTokenForms() throws Exception {
        needSemgrep();
        // explicit files: Semgrep's default ignore patterns skip any directory named "test"
        List<Path> files;
        try (java.util.stream.Stream<Path> s = java.nio.file.Files.list(Path.of("src/test/resources/semgrep/jsp"))) {
            files = s.sorted().toList();
        }
        SemgrepRunner.Result r = SemgrepRunner.run(RULES, files, "src/test/resources/semgrep/jsp/");
        List<String> flagged = r.findings().stream().map(SemgrepRunner.Raw::file).sorted().toList();
        assertEquals(List.of("plain-post.jsp", "struts-default-method.jsp", "struts-multiline-upper.jsp", "struts-post.jsp"), flagged);
    }

    @Test
    void theRealLegacyAppHasExactlyTheSeededFindings() throws Exception {
        needSemgrep();
        SemgrepRunner.Result r = SemgrepRunner.run(RULES, Path.of("..", "legacy-app", "src", "main"), "../legacy-app/");
        Map<String, Integer> c = counts(r);
        assertEquals(3, c.get("harbor.sql-concat-taint"));
        assertEquals(1, c.get("harbor.plaintext-password-compare"));
        assertEquals(1, c.get("harbor.login-session-reuse"));
        assertEquals(10, c.get("harbor.struts-post-form-without-token") + c.get("harbor.html-post-form-without-token"));
        assertTrue(!c.containsKey("harbor.prepared-statement-concat"), "no PreparedStatement is built from concatenation");
        List<String> sqlFiles = r.findings().stream().filter(f -> f.ruleId().equals("harbor.sql-concat-taint")).map(SemgrepRunner.Raw::file).toList();
        assertEquals(List.of("src/main/java/com/harborclaims/portal/admin/AdminUserDao.java",
                "src/main/java/com/harborclaims/portal/claims/ClaimDao.java",
                "src/main/java/com/harborclaims/portal/customers/CustomerDao.java"), sqlFiles);
    }

    @Test
    void parsingNormalisesIdsPathsAndOrder() throws Exception {
        String a = "{\"results\":[{\"check_id\":\"x.y.harbor.b\",\"path\":\"app/B.java\",\"start\":{\"line\":9},\"extra\":{\"message\":\"m\"}},"
                + "{\"check_id\":\"harbor.a\",\"path\":\"app/A.java\",\"start\":{\"line\":2},\"extra\":{\"message\":\"m\"}}],\"errors\":[]}";
        String b = "{\"results\":[{\"check_id\":\"harbor.a\",\"path\":\"app/A.java\",\"start\":{\"line\":2},\"extra\":{\"message\":\"m\"}},"
                + "{\"check_id\":\"x.y.harbor.b\",\"path\":\"app/B.java\",\"start\":{\"line\":9},\"extra\":{\"message\":\"m\"}}],\"errors\":[]}";
        SemgrepRunner.Result ra = SemgrepRunner.parse(a, "app/", "1.0");
        SemgrepRunner.Result rb = SemgrepRunner.parse(b, "app/", "1.0");
        assertEquals(ra.findings(), rb.findings(), "input order must not matter");
        assertEquals("harbor.a", ra.findings().get(0).ruleId());
        assertEquals("harbor.b", ra.findings().get(1).ruleId());
        assertEquals("A.java", ra.findings().get(0).file());
    }
}
