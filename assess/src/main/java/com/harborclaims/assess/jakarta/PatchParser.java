package com.harborclaims.assess.jakarta;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the file list from a unified diff produced by OpenRewrite. */
public final class PatchParser {

    public record Change(String path, String renamedFrom) {
    }

    private static final Pattern HEADER = Pattern.compile("(?m)^diff --git a/(\\S+) b/(\\S+)\\s*$");

    private PatchParser() {
    }

    /** Unique files in order of first appearance. No patch text means no changes. */
    public static List<Change> parse(String patch) {
        List<Change> out = new ArrayList<>();
        if (patch == null) {
            return out;
        }
        Matcher m = HEADER.matcher(patch);
        while (m.find()) {
            String from = m.group(1);
            String to = m.group(2);
            Change c = new Change(to, from.equals(to) ? null : from);
            if (out.stream().noneMatch(x -> x.path().equals(c.path()))) {
                out.add(c);
            }
        }
        return out;
    }

    public static String kindOf(String path) {
        if (path.endsWith(".jsp")) {
            return "jsp";
        }
        if (path.startsWith("src/main/java/") && path.endsWith(".java")) {
            return "mainJava";
        }
        if (path.startsWith("src/test/java/") && path.endsWith(".java")) {
            return "testJava";
        }
        if (path.equals("pom.xml")) {
            return "pom";
        }
        if (path.endsWith(".xml")) {
            return "xml";
        }
        return "other";
    }
}
