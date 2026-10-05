package com.harborclaims.assess.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The single module map shared by inventory, structure and estimate (assess/modules.yaml).
 * Anything the map does not cover is an error: there is no "Other" bucket.
 */
public final class Modules {

    private record Module(String name, List<String> pathPrefixes, List<String> packages, List<Pattern> jsps) {
    }

    private record Scope(String module, List<String> actions) {
    }

    private final List<Module> modules = new ArrayList<>();
    private final List<Scope> scope = new ArrayList<>();

    public static Modules load(Path yaml) throws IOException {
        JsonNode root = new ObjectMapper(new YAMLFactory()).readTree(Files.readAllBytes(yaml));
        Modules m = new Modules();
        for (JsonNode n : root.get("modules")) {
            List<Pattern> jsps = new ArrayList<>();
            for (JsonNode j : n.path("jsps")) {
                jsps.add(glob(j.asText()));
            }
            m.modules.add(new Module(n.get("name").asText(), strings(n.path("pathPrefixes")),
                    strings(n.path("packages")), jsps));
        }
        for (JsonNode n : root.path("migrationScope")) {
            m.scope.add(new Scope(n.get("module").asText(), strings(n.path("actions"))));
        }
        return m;
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(x -> out.add(x.asText()));
        return out;
    }

    private static Pattern glob(String g) {
        return Pattern.compile(Pattern.quote(g).replace("*", "\\E.*\\Q"));
    }

    public List<String> names() {
        return modules.stream().map(Module::name).toList();
    }

    public String forActionPath(String path) {
        for (Module m : modules) {
            for (String prefix : m.pathPrefixes) {
                if (path.startsWith(prefix)) {
                    return m.name;
                }
            }
        }
        throw new IllegalStateException("Action path is not covered by modules.yaml: " + path);
    }

    /** Longest matching package prefix wins. */
    public String forPackage(String pkg) {
        String best = null;
        int bestLen = -1;
        for (Module m : modules) {
            for (String p : m.packages) {
                if ((pkg.equals(p) || pkg.startsWith(p + ".")) && p.length() > bestLen) {
                    best = m.name;
                    bestLen = p.length();
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException("Package is not covered by modules.yaml: " + pkg);
        }
        return best;
    }

    public String forJsp(String fileName) {
        for (Module m : modules) {
            for (Pattern p : m.jsps) {
                if (p.matcher(fileName).matches()) {
                    return m.name;
                }
            }
        }
        throw new IllegalStateException("JSP is not covered by modules.yaml: " + fileName);
    }

    public boolean inMigrationScope(String actionPath) {
        String module = forActionPath(actionPath);
        for (Scope s : scope) {
            if (s.module.equals(module) && (s.actions.isEmpty() || s.actions.contains(actionPath))) {
                return true;
            }
        }
        return false;
    }
}
