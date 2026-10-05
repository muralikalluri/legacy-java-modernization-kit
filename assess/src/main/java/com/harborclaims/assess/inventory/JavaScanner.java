package com.harborclaims.assess.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a Java source file without a full parser: comments and string/char literals are blanked out first,
 * so a javax name inside a comment or a string is never counted.
 */
public final class JavaScanner {

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z0-9_.]+)\\s*;");
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z0-9_.]+?)(\\.\\*)?\\s*;");
    private static final Pattern QUALIFIED = Pattern.compile("\\bjavax(?:\\.[a-z][A-Za-z0-9_]*)+");
    private static final Pattern TYPE = Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Za-z0-9_]+)");

    public record Result(String pkg, String className, int lines, List<String> javaxImports, List<String> javaxQualified) {
    }

    private JavaScanner() {
    }

    public static Result scan(String source) {
        String code = strip(source);
        Matcher pm = PACKAGE.matcher(code);
        String pkg = pm.find() ? pm.group(1) : "";

        List<String> imports = new ArrayList<>();
        Matcher im = IMPORT.matcher(code);
        StringBuilder body = new StringBuilder();
        int last = 0;
        while (im.find()) {
            body.append(code, last, im.start());
            last = im.end();
            String name = im.group(1);
            if (name.startsWith("javax.")) {
                imports.add(Javax.packageOf(name));
            }
        }
        body.append(code.substring(last));

        List<String> qualified = new ArrayList<>();
        Matcher qm = QUALIFIED.matcher(body);
        while (qm.find()) {
            qualified.add(Javax.packageOf(qm.group()));
        }

        Matcher tm = TYPE.matcher(code);
        String type = tm.find() ? tm.group(1) : "";
        int lines = source.isEmpty() ? 0 : (int) source.chars().filter(c -> c == '\n').count() + (source.endsWith("\n") ? 0 : 1);
        return new Result(pkg, type, lines, imports, qualified);
    }

    /** Replaces comments, string literals and char literals with spaces (newlines kept). */
    public static String strip(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            char d = i + 1 < n ? s.charAt(i + 1) : '\0';
            if (c == '/' && d == '/') {
                while (i < n && s.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && d == '*') {
                out.append("  ");
                i += 2;
                while (i < n && !(s.charAt(i) == '*' && i + 1 < n && s.charAt(i + 1) == '/')) {
                    out.append(s.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("  ");
                    i += 2;
                }
            } else if (c == '"' || c == '\'') {
                out.append(' ');
                i++;
                while (i < n && s.charAt(i) != c && s.charAt(i) != '\n') {
                    if (s.charAt(i) == '\\' && i + 1 < n) {
                        out.append(' ');
                        i++;
                    }
                    out.append(' ');
                    i++;
                }
                if (i < n && s.charAt(i) == c) {
                    out.append(' ');
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
