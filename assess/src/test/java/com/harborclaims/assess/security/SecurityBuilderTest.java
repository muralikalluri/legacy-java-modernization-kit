package com.harborclaims.assess.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.harborclaims.assess.common.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecurityBuilderTest {

    @TempDir
    Path tmp;

    private static JsonNode inventory() throws Exception {
        return Json.MAPPER.readTree("""
                {"inputsFingerprint":"abc","actions":[
                 {"path":"/admin/users","rolesDeclared":["ADMIN"]},
                 {"path":"/admin/userSave","rolesDeclared":null},
                 {"path":"/admin/jobRun","rolesDeclared":null},
                 {"path":"/customers/save","rolesDeclared":["CSR"]},
                 {"path":"/customers/list","rolesDeclared":null},
                 {"path":"/login","rolesDeclared":null}]}""");
    }

    private static SemgrepRunner.Result semgrep(SemgrepRunner.Raw... raws) {
        return new SemgrepRunner.Result("1.0", List.of(raws), 0);
    }

    @SuppressWarnings("unchecked")
    private static List<SecurityBuilder.Finding> findings(Map<String, Object> out) {
        return (List<SecurityBuilder.Finding>) out.get("findings");
    }

    @Test
    void onlyAdminMappingsWithoutRolesAreMissingAuthorization() throws Exception {
        Map<String, Object> out = SecurityBuilder.build(inventory(), semgrep(), 0, "fp");
        SecurityBuilder.Finding f = findings(out).stream().filter(x -> x.category().equals("MISSING_AUTHORIZATION")).findFirst().orElseThrow();
        assertEquals(2, f.count());
        assertEquals(List.of("/admin/jobRun", "/admin/userSave"), f.locations().stream().map(SecurityBuilder.Location::path).toList());
    }

    @Test
    void threeSqlSitesAreOneFindingWithThreeSites() throws Exception {
        Map<String, Object> out = SecurityBuilder.build(inventory(), semgrep(
                new SemgrepRunner.Raw("harbor.sql-concat-taint", "a/A.java", 3, "m"),
                new SemgrepRunner.Raw("harbor.sql-concat-taint", "a/B.java", 4, "m"),
                new SemgrepRunner.Raw("harbor.sql-concat-taint", "a/C.java", 5, "m")), 0, "fp");
        List<SecurityBuilder.Finding> sql = findings(out).stream().filter(x -> x.category().equals("SQL_INJECTION")).toList();
        assertEquals(1, sql.size());
        assertEquals(3, sql.get(0).count());
    }

    @Test
    void csrfIsOneAppWideFindingListingTheMutatingMappings() throws Exception {
        Map<String, Object> out = SecurityBuilder.build(inventory(), semgrep(
                new SemgrepRunner.Raw("harbor.struts-post-form-without-token", "a.jsp", 1, "m"),
                new SemgrepRunner.Raw("harbor.html-post-form-without-token", "b.jsp", 1, "m")), 0, "fp");
        List<SecurityBuilder.Finding> csrf = findings(out).stream().filter(x -> x.category().equals("CSRF")).toList();
        assertEquals(1, csrf.size());
        assertEquals(2, csrf.get(0).count());
        assertEquals(0, csrf.get(0).evidence().get("tokenChecksInActions"));
        assertEquals(List.of("/admin/jobRun", "/admin/userSave", "/customers/save"), csrf.get(0).evidence().get("mutatingMappings"));
    }

    @Test
    void noRawFindingsMeansNoSemgrepCategories() throws Exception {
        Map<String, Object> out = SecurityBuilder.build(inventory(), semgrep(), 0, "fp");
        assertEquals(List.of("MISSING_AUTHORIZATION"), findings(out).stream().map(SecurityBuilder.Finding::category).toList());
    }

    @Test
    void findingsAreSortedByCategory() throws Exception {
        Map<String, Object> out = SecurityBuilder.build(inventory(), semgrep(
                new SemgrepRunner.Raw("harbor.plaintext-password-compare", "a.java", 1, "m"),
                new SemgrepRunner.Raw("harbor.sql-concat-taint", "b.java", 1, "m")), 0, "fp");
        List<String> cats = findings(out).stream().map(SecurityBuilder.Finding::category).toList();
        assertEquals(cats.stream().sorted().toList(), cats);
    }

    @Test
    void tokenChecksCountCodeNotCommentsOrStrings() throws Exception {
        Path java = tmp.resolve("A.java");
        Files.writeString(java, """
                class A {
                    // saveToken(request);
                    String s = "isTokenValid(request)";
                    void ok() { saveToken(request); if (isTokenValid(request)) { resetToken(request); } }
                }
                """);
        assertEquals(3, SecurityBuilder.tokenChecks(tmp));
    }

    @Test
    void theFingerprintChangesWhenASourceOrRuleChanges() throws Exception {
        Path src = tmp.resolve("src/main/java");
        Files.createDirectories(src);
        Path rules = tmp.resolve("rules");
        Files.createDirectories(rules);
        Files.writeString(src.resolve("A.java"), "class A {}");
        Files.writeString(rules.resolve("r.yaml"), "rules: []");
        String before = SecurityBuilder.semgrepInputsFingerprint(tmp, rules);
        assertEquals(before, SecurityBuilder.semgrepInputsFingerprint(tmp, rules));
        Files.writeString(src.resolve("A.java"), "class A { int x; }");
        assertTrue(!before.equals(SecurityBuilder.semgrepInputsFingerprint(tmp, rules)));
    }
}
