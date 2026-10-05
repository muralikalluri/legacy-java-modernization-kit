package com.harborclaims.assess.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.harborclaims.assess.common.Hashing;
import com.harborclaims.assess.common.SourceTree;
import com.harborclaims.assess.inventory.JavaScanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds security.json. It states facts only (category, count, locations, CWE). Severity, likelihood and cost are
 * judgements and live in the estimation model, not here.
 */
public final class SecurityBuilder {

    public record Location(String file, Integer line, String path) {
    }

    public record Finding(String category, String title, String cwe, int count, String countUnit, List<Location> locations,
            Map<String, Object> evidence, List<String> sources) {
    }

    private static final Pattern MUTATING =
            Pattern.compile("/(save|delete|cancel|renew|transition|note|userSave|userToggle|jobRun)$");
    private static final Pattern TOKEN_CALL = Pattern.compile("\\b(saveToken|isTokenValid|resetToken)\\s*\\(");

    private SecurityBuilder() {
    }

    public static int tokenChecks(Path mainJava) throws IOException {
        int n = 0;
        for (Path p : SourceTree.files(mainJava, x -> x.toString().endsWith(".java"))) {
            String code = JavaScanner.strip(Files.readString(p, StandardCharsets.UTF_8));
            Matcher m = TOKEN_CALL.matcher(code);
            while (m.find()) {
                n++;
            }
        }
        return n;
    }

    /** Hash of everything Semgrep reads (sources and rules), so a reused result can be checked for staleness. */
    public static String semgrepInputsFingerprint(Path root, Path rulesDir) throws IOException {
        List<String> lines = new ArrayList<>();
        Path main = root.resolve("src/main");
        for (Path p : SourceTree.files(main, x -> true)) {
            lines.add("src/main/" + SourceTree.rel(main, p) + ":" + Hashing.sha256(p));
        }
        for (Path p : SourceTree.files(rulesDir, x -> true)) {
            lines.add("rules/" + SourceTree.rel(rulesDir, p) + ":" + Hashing.sha256(p));
        }
        return Hashing.fingerprint(lines);
    }

    public static Map<String, Object> build(JsonNode inventory, SemgrepRunner.Result semgrep, int tokenChecks,
            String semgrepFingerprint) {
        List<Finding> findings = new ArrayList<>();

        List<SemgrepRunner.Raw> sql = of(semgrep, "harbor.sql-concat-taint", "harbor.prepared-statement-concat");
        if (!sql.isEmpty()) {
            Map<String, Object> ev = new TreeMap<>();
            ev.put("note", "Each site is one SQL statement built by concatenating a String method parameter.");
            findings.add(new Finding("SQL_INJECTION", "SQL built by string concatenation", "CWE-89", sql.size(), "query sites",
                    locations(sql), ev, List.of("semgrep")));
        }
        List<SemgrepRunner.Raw> pw = of(semgrep, "harbor.plaintext-password-compare");
        if (!pw.isEmpty()) {
            Map<String, Object> ev = new TreeMap<>();
            ev.put("note", "Passwords are compared with equals() on the stored value, so they are stored in plaintext.");
            findings.add(new Finding("PLAINTEXT_PASSWORD", "Passwords stored and compared in plaintext", "CWE-256", pw.size(),
                    "comparison sites", locations(pw), ev, List.of("semgrep")));
        }
        List<SemgrepRunner.Raw> forms = of(semgrep, "harbor.struts-post-form-without-token", "harbor.html-post-form-without-token");
        if (!forms.isEmpty()) {
            Map<String, Object> ev = new TreeMap<>();
            List<String> mutating = new ArrayList<>();
            for (JsonNode a : inventory.get("actions")) {
                if (MUTATING.matcher(a.get("path").asText()).find()) {
                    mutating.add(a.get("path").asText());
                }
            }
            mutating.sort(String::compareTo);
            ev.put("postFormsWithoutToken", forms.size());
            ev.put("tokenChecksInActions", tokenChecks);
            ev.put("mutatingMappings", mutating);
            ev.put("note", "Reported once for the whole app. Some mutating mappings (delete, toggle) are reached by GET links, "
                    + "so they are exposed even though no form posts to them.");
            findings.add(new Finding("CSRF", "State-changing forms without an anti-CSRF token", "CWE-352", forms.size(),
                    "POST forms", locations(forms), ev, List.of("semgrep", "source scan")));
        }
        List<SemgrepRunner.Raw> session = of(semgrep, "harbor.login-session-reuse");
        if (!session.isEmpty()) {
            Map<String, Object> ev = new TreeMap<>();
            ev.put("note", "The session id is not renewed when a user signs in.");
            findings.add(new Finding("SESSION_FIXATION", "Session id not renewed at login", "CWE-384", session.size(), "sites",
                    locations(session), ev, List.of("semgrep")));
        }

        // Cross-reference with the inventory: an /admin/ mapping without a roles= attribute has no authorization check.
        List<Location> open = new ArrayList<>();
        int adminActions = 0;
        for (JsonNode a : inventory.get("actions")) {
            String path = a.get("path").asText();
            if (path.startsWith("/admin/")) {
                adminActions++;
                if (a.get("rolesDeclared").isNull()) {
                    open.add(new Location(null, null, path));
                }
            }
        }
        open.sort((x, y) -> x.path().compareTo(y.path()));
        if (!open.isEmpty()) {
            Map<String, Object> ev = new TreeMap<>();
            ev.put("adminActions", adminActions);
            ev.put("rule", "an action under /admin/ whose mapping has no roles= attribute");
            findings.add(new Finding("MISSING_AUTHORIZATION", "Admin actions without a role check", "CWE-862", open.size(),
                    "actions", open, ev, List.of("inventory")));
        }
        findings.sort((a, b) -> a.category().compareTo(b.category()));

        Map<String, Integer> byCategory = new TreeMap<>();
        findings.forEach(f -> byCategory.put(f.category(), f.count()));

        Map<String, Object> summary = new TreeMap<>();
        summary.put("categories", findings.size());
        summary.put("sitesByCategory", byCategory);
        summary.put("semgrepRawFindings", semgrep.findings().size());
        summary.put("semgrepErrors", semgrep.errors());

        Map<String, Object> sg = new TreeMap<>();
        sg.put("version", semgrep.version());
        sg.put("config", "assess/rules");
        sg.put("inputsFingerprint", semgrepFingerprint);
        sg.put("rawFindings", semgrep.findings());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schema", 1);
        out.put("tool", "assess 1.0.0");
        out.put("source", "legacy-app");
        out.put("inventoryFingerprint", inventory.get("inputsFingerprint").asText());
        out.put("summary", summary);
        out.put("findings", findings);
        out.put("semgrep", sg);
        out.put("notes", List.of(
                "Facts only: severity, likelihood and fix cost are assigned in the estimation model, not here.",
                "SQL injection sites are counted as query sites: one statement is one site even if it concatenates two parameters.",
                "A missing roles= attribute means three different things (public, any signed-in user, a flaw); only /admin/ mappings are judged."));
        return out;
    }

    private static List<SemgrepRunner.Raw> of(SemgrepRunner.Result r, String... ids) {
        List<String> wanted = List.of(ids);
        return r.findings().stream().filter(f -> wanted.contains(f.ruleId())).toList();
    }

    private static List<Location> locations(List<SemgrepRunner.Raw> raws) {
        return raws.stream().map(r -> new Location(r.file(), r.line(), null)).toList();
    }
}
