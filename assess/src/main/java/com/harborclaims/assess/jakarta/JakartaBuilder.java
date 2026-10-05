package com.harborclaims.assess.jakarta;

import com.harborclaims.assess.common.Hashing;
import com.harborclaims.assess.common.SourceTree;
import com.harborclaims.assess.inventory.JavaScanner;
import com.harborclaims.assess.inventory.Javax;
import com.harborclaims.assess.inventory.JspScanner;
import com.harborclaims.assess.inventory.WebXml;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Builds jakarta.json: how much of the legacy app depends on javax.* and what an automated rewrite would touch. */
public final class JakartaBuilder {

    private JakartaBuilder() {
    }

    /** Hash of everything OpenRewrite reads (Java sources and the pom), so a reused dry-run result can be checked for staleness. */
    public static String sourcesFingerprint(Path root) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String dir : new String[] {"src/main/java", "src/test/java"}) {
            for (Path p : SourceTree.files(root.resolve(dir), x -> x.toString().endsWith(".java"))) {
                lines.add(SourceTree.rel(root, p) + ":" + Hashing.sha256(p));
            }
        }
        lines.add("pom.xml:" + Hashing.sha256(root.resolve("pom.xml")));
        lines.add("web.xml:" + Hashing.sha256(root.resolve("src/main/webapp/WEB-INF/web.xml")));
        lines.add("rewrite:" + RewriteRunner.PLUGIN + ":" + RewriteRunner.RECIPE_ARTIFACT + ":" + RewriteRunner.RECIPE);
        return Hashing.fingerprint(lines);
    }

    public static Map<String, Object> build(Path root, Map<String, Object> rewriteBlock) throws IOException {
        List<String> fingerprint = new ArrayList<>();
        Map<String, Object> main = scanJava(root, root.resolve("src/main/java"), fingerprint);
        Map<String, Object> test = scanJava(root, root.resolve("src/test/java"), fingerprint);

        Path webapp = root.resolve("src/main/webapp");
        Map<String, Integer> jspJavax = new TreeMap<>();
        int jspFilesWithJavax = 0;
        int jstlUris = 0;
        int jspCount = 0;
        for (Path p : SourceTree.files(webapp, x -> x.toString().endsWith(".jsp"))) {
            String text = Files.readString(p, StandardCharsets.UTF_8);
            fingerprint.add("webapp/" + SourceTree.rel(webapp, p) + ":" + Hashing.sha256(text.getBytes(StandardCharsets.UTF_8)));
            JspScanner.Result r = JspScanner.scan(text);
            jspCount++;
            jstlUris += r.jstlUris();
            if (!r.javaxByPackage().isEmpty()) {
                jspFilesWithJavax++;
            }
            r.javaxByPackage().forEach((k, v) -> jspJavax.merge(k, v, Integer::sum));
        }

        WebXml.Parsed web = WebXml.parse(webapp.resolve("WEB-INF/web.xml"));
        fingerprint.add("web.xml:" + Hashing.sha256(webapp.resolve("WEB-INF/web.xml")));

        Map<String, Object> jsp = new TreeMap<>();
        jsp.put("files", jspCount);
        jsp.put("filesWithJavaxReferences", jspFilesWithJavax);
        jsp.put("javaxReferencesByPackage", jspJavax);
        jsp.put("javaxReferences", jspJavax.values().stream().mapToInt(Integer::intValue).sum());
        jsp.put("jstlUris", jstlUris);

        Map<String, Object> config = new TreeMap<>();
        config.put("webXmlNamespace", web.namespace());
        config.put("webXmlVersion", web.version());
        config.put("webXmlNeedsJakartaNamespace", web.namespace().contains("java.sun.com/xml/ns/javaee"));

        fingerprint.add("rewrite:" + RewriteRunner.PLUGIN + ":" + RewriteRunner.RECIPE_ARTIFACT + ":" + RewriteRunner.RECIPE);

        int jakartaImports = (int) main.get("jakartaMigratingImports");
        int jakartaQualified = (int) main.get("jakartaMigratingQualifiedUses");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schema", 1);
        out.put("tool", "assess 1.0.0");
        out.put("source", "legacy-app");
        out.put("inputsFingerprint", Hashing.fingerprint(fingerprint));
        out.put("javaMain", main);
        out.put("javaTest", test);
        out.put("jsp", jsp);
        out.put("config", config);
        out.put("rewrite", rewriteBlock);
        out.put("notes", List.of(
                "JDK-owned packages (javax.sql, javax.naming, javax.xml.parsers, ...) stay javax after a Jakarta migration.",
                "Imports are read from Java sources with comments and string literals removed.",
                "OpenRewrite does not rewrite JSPs, so JSP javax references are reported separately.",
                "There is no Jakarta-compatible Struts 1: after the rewrite, struts-core still needs javax.servlet. "
                        + "The changed-file count measures the javax surface, not whether a migration is feasible.",
                "Jakarta-migrating javax imports in main sources: " + jakartaImports + " import statements and " + jakartaQualified
                        + " fully qualified uses move to jakarta."));
        return out;
    }

    private static Map<String, Object> scanJava(Path root, Path dir, List<String> fingerprint) throws IOException {
        Map<String, Integer> byPackage = new TreeMap<>();
        Map<String, Integer> qualifiedByPackage = new TreeMap<>();
        int files = 0;
        int filesWithJavax = 0;
        int filesWithJakartaMigrating = 0;
        for (Path p : SourceTree.files(dir, x -> x.toString().endsWith(".java"))) {
            String text = Files.readString(p, StandardCharsets.UTF_8);
            fingerprint.add(SourceTree.rel(root, p) + ":" + Hashing.sha256(text.getBytes(StandardCharsets.UTF_8)));
            JavaScanner.Result r = JavaScanner.scan(text);
            files++;
            if (!r.javaxImports().isEmpty() || !r.javaxQualified().isEmpty()) {
                filesWithJavax++;
            }
            boolean moves = r.javaxImports().stream().anyMatch(k -> Javax.bucket(k).equals(Javax.JAKARTA))
                    || r.javaxQualified().stream().anyMatch(k -> Javax.bucket(k).equals(Javax.JAKARTA));
            if (moves) {
                filesWithJakartaMigrating++;
            }
            r.javaxImports().forEach(k -> byPackage.merge(k, 1, Integer::sum));
            r.javaxQualified().forEach(k -> qualifiedByPackage.merge(k, 1, Integer::sum));
        }
        Map<String, Object> out = new TreeMap<>();
        out.put("files", files);
        out.put("filesWithJavax", filesWithJavax);
        out.put("filesWithJakartaMigratingJavax", filesWithJakartaMigrating);
        out.put("javaxImports", byPackage.values().stream().mapToInt(Integer::intValue).sum());
        out.put("importsByPackage", byPackage);
        out.put("qualifiedUsesByPackage", qualifiedByPackage);
        out.put("jakartaMigratingImports", sum(byPackage, Javax.JAKARTA));
        out.put("jdkOwnedImports", sum(byPackage, Javax.JDK));
        out.put("otherImports", sum(byPackage, Javax.OTHER));
        out.put("jakartaMigratingQualifiedUses", sum(qualifiedByPackage, Javax.JAKARTA));
        out.put("jdkOwnedQualifiedUses", sum(qualifiedByPackage, Javax.JDK));
        return out;
    }

    private static int sum(Map<String, Integer> byPackage, String bucket) {
        return byPackage.entrySet().stream().filter(e -> Javax.bucket(e.getKey()).equals(bucket))
                .mapToInt(Map.Entry::getValue).sum();
    }
}
