package com.harborclaims.assess.inventory;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Counts scriptlets in a JSP. Every occurrence counts (several can sit on one line). */
public final class JspScanner {

    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern OPEN = Pattern.compile("(?<!\\\\)<%");
    private static final Pattern XML_SCRIPTLET = Pattern.compile("<jsp:scriptlet\\b");
    private static final Pattern XML_EXPRESSION = Pattern.compile("<jsp:expression\\b");
    private static final Pattern XML_DECLARATION = Pattern.compile("<jsp:declaration\\b");
    private static final Pattern JAVAX = Pattern.compile("\\bjavax(?:\\.[a-z][A-Za-z0-9_]*)+");
    private static final Pattern JSTL = Pattern.compile("http://java\\.sun\\.com/jsp/jstl/");

    public record Result(int lines, int scriptlets, int expressions, int declarations, int jstlUris,
            Map<String, Integer> javaxByPackage) {

        public int totalScriptletBlocks() {
            return scriptlets + expressions + declarations;
        }
    }

    private JspScanner() {
    }

    public static Result scan(String source) {
        String text = JSP_COMMENT.matcher(source).replaceAll(m -> " ".repeat(0));
        int scriptlets = 0;
        int expressions = 0;
        int declarations = 0;
        Matcher m = OPEN.matcher(text);
        while (m.find()) {
            char next = m.end() < text.length() ? text.charAt(m.end()) : '\0';
            if (next == '@') {
                continue;
            }
            if (next == '=') {
                expressions++;
            } else if (next == '!') {
                declarations++;
            } else {
                scriptlets++;
            }
        }
        scriptlets += count(XML_SCRIPTLET, text);
        expressions += count(XML_EXPRESSION, text);
        declarations += count(XML_DECLARATION, text);

        Map<String, Integer> javax = new TreeMap<>();
        Matcher jm = JAVAX.matcher(text);
        while (jm.find()) {
            javax.merge(Javax.packageOf(jm.group()), 1, Integer::sum);
        }
        int lines = source.isEmpty() ? 0 : (int) source.chars().filter(c -> c == '\n').count() + (source.endsWith("\n") ? 0 : 1);
        return new Result(lines, scriptlets, expressions, declarations, count(JSTL, text), javax);
    }

    private static int count(Pattern p, String text) {
        Matcher m = p.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
