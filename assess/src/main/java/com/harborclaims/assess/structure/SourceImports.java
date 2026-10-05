package com.harborclaims.assess.structure;

import com.harborclaims.assess.common.SourceTree;
import com.harborclaims.assess.inventory.Javax;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Package-to-package edges read from import statements, used to cross-check the jdeps graph. */
public final class SourceImports {

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([A-Za-z0-9_.]+)\\s*;");
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z0-9_.]+?)(\\.\\*)?\\s*;");

    private SourceImports() {
    }

    /** Edges as "fromPackage -> toPackage", app packages only, self edges dropped. */
    public static Set<String> packageEdges(Path mainJava) throws IOException {
        Set<String> edges = new TreeSet<>();
        List<Path> files = SourceTree.files(mainJava, p -> p.toString().endsWith(".java"));
        for (Path f : files) {
            String text = Files.readString(f, StandardCharsets.UTF_8);
            Matcher pm = PACKAGE.matcher(text);
            if (!pm.find()) {
                continue;
            }
            String from = pm.group(1);
            Matcher im = IMPORT.matcher(text);
            while (im.find()) {
                String name = im.group(1);
                if (!name.startsWith(JdepsRunner.APP_PREFIX)) {
                    continue;
                }
                String to = Javax.packageOf(name);
                if (!to.equals(from)) {
                    edges.add(from + " -> " + to);
                }
            }
        }
        return edges;
    }
}
