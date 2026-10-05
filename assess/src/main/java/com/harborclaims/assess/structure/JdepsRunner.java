package com.harborclaims.assess.structure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runs the JDK's jdeps on the compiled classes and returns distinct class-to-class edges inside the app. */
public final class JdepsRunner {

    public static final String APP_PREFIX = "com.harborclaims.portal.";

    private static final Pattern LINE = Pattern.compile("^\\s+(com\\.harborclaims\\.\\S+)\\s+->\\s+(com\\.harborclaims\\.\\S+)\\s+\\S+\\s*$");

    private JdepsRunner() {
    }

    public static List<String> command(Path classesDir) {
        String jdeps = Path.of(System.getProperty("java.home"), "bin", "jdeps").toString();
        return List.of(jdeps, "-verbose:class", "-filter:none", "-regex", "com\\.harborclaims\\..*", classesDir.toString());
    }

    /** Edges as "from -> to" strings of top-level classes (inner classes fold into their outer class). */
    public static Set<String> run(Path classesDir) throws IOException, InterruptedException {
        if (!Files.isDirectory(classesDir)) {
            throw new IOException("No compiled classes at " + classesDir + " (build legacy-app first)");
        }
        Process p = new ProcessBuilder(command(classesDir)).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) {
            throw new IOException("jdeps failed: " + out);
        }
        return parse(out);
    }

    public static Set<String> parse(String output) {
        Set<String> edges = new TreeSet<>();
        for (String line : output.split("\n")) {
            Matcher m = LINE.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String from = outer(m.group(1));
            String to = outer(m.group(2));
            if (!from.equals(to) && from.startsWith(APP_PREFIX) && to.startsWith(APP_PREFIX)) {
                edges.add(from + " -> " + to);
            }
        }
        return edges;
    }

    static String outer(String className) {
        int dollar = className.indexOf('$');
        return dollar < 0 ? className : className.substring(0, dollar);
    }

    public static String packageOf(String className) {
        return className.substring(0, className.lastIndexOf('.'));
    }
}
