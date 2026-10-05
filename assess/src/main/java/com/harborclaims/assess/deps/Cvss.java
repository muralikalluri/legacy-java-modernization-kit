package com.harborclaims.assess.deps;

import java.util.HashMap;
import java.util.Map;

/** CVSS v3.0/v3.1 base score from a vector string. Returns null for anything else (v2, v4, malformed). */
public final class Cvss {

    private Cvss() {
    }

    public static Double baseScore(String vector) {
        if (vector == null || !(vector.startsWith("CVSS:3.1/") || vector.startsWith("CVSS:3.0/"))) {
            return null;
        }
        Map<String, String> m = new HashMap<>();
        for (String part : vector.substring(9).split("/")) {
            String[] kv = part.split(":");
            if (kv.length != 2) {
                return null;
            }
            m.put(kv[0], kv[1]);
        }
        Double av = pick(m.get("AV"), "N", 0.85, "A", 0.62, "L", 0.55, "P", 0.2);
        Double ac = pick(m.get("AC"), "L", 0.77, "H", 0.44);
        Double ui = pick(m.get("UI"), "N", 0.85, "R", 0.62);
        Double c = pick(m.get("C"), "H", 0.56, "L", 0.22, "N", 0.0);
        Double i = pick(m.get("I"), "H", 0.56, "L", 0.22, "N", 0.0);
        Double a = pick(m.get("A"), "H", 0.56, "L", 0.22, "N", 0.0);
        String s = m.get("S");
        if (av == null || ac == null || ui == null || c == null || i == null || a == null || s == null
                || !(s.equals("U") || s.equals("C"))) {
            return null;
        }
        boolean changed = s.equals("C");
        Double pr = changed ? pick(m.get("PR"), "N", 0.85, "L", 0.68, "H", 0.5) : pick(m.get("PR"), "N", 0.85, "L", 0.62, "H", 0.27);
        if (pr == null) {
            return null;
        }
        double iss = 1 - (1 - c) * (1 - i) * (1 - a);
        double impact = changed ? 7.52 * (iss - 0.029) - 3.25 * Math.pow(iss - 0.02, 15) : 6.42 * iss;
        double exploitability = 8.22 * av * ac * pr * ui;
        if (impact <= 0) {
            return 0.0;
        }
        double raw = changed ? Math.min(1.08 * (impact + exploitability), 10) : Math.min(impact + exploitability, 10);
        return roundUp(raw);
    }

    private static Double pick(String key, Object... pairs) {
        if (key == null) {
            return null;
        }
        for (int n = 0; n < pairs.length; n += 2) {
            if (pairs[n].equals(key)) {
                return (Double) pairs[n + 1];
            }
        }
        return null;
    }

    /** The CVSS 3.1 round-up rule, safe against floating point noise. */
    static double roundUp(double value) {
        long scaled = Math.round(value * 100000);
        if (scaled % 10000 == 0) {
            return scaled / 100000.0;
        }
        return (Math.floor(scaled / 10000.0) + 1) / 10.0;
    }

    public static String label(double score) {
        if (score == 0.0) {
            return "NONE";
        }
        if (score < 4.0) {
            return "LOW";
        }
        if (score < 7.0) {
            return "MEDIUM";
        }
        if (score < 9.0) {
            return "HIGH";
        }
        return "CRITICAL";
    }
}
