package com.harborclaims.assess.report;

import java.math.BigDecimal;
import java.util.Locale;

/** Markdown-safe text and number formatting. All of it happens here so the template stays free of logic and digits. */
public final class Md {

    private Md() {
    }

    /** Escapes free text for use inside Markdown, including table cells. Collapses whitespace and newlines. */
    public static String text(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\s+", " ").trim();
        StringBuilder sb = new StringBuilder();
        for (char c : t.toCharArray()) {
            if ("\\`*_{}[]<>|#".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** A number without trailing zeros: 225.0 gives 225 and 88.50 gives 88.5. */
    public static String num(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        return (s.scale() < 0 ? s.setScale(0) : s).toPlainString();
    }

    public static String range(BigDecimal low, BigDecimal high) {
        return num(low).equals(num(high)) ? num(low) : num(low) + " to " + num(high);
    }

    public static String plural(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    /** "3 query sites" stays plural; with a count of one the trailing "s" goes: "1 query site". */
    public static String unit(int count, String pluralUnit) {
        return count + " " + (count == 1 && pluralUnit.endsWith("s") ? pluralUnit.substring(0, pluralUnit.length() - 1) : pluralUnit);
    }

    public static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    public static String shortName(String qualified) {
        return qualified.substring(qualified.lastIndexOf('.') + 1);
    }
}
