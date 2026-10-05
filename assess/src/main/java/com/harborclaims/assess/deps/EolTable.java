package com.harborclaims.assess.deps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reference table of end-of-life frameworks. Evaluated against the table's own asOf date, never the clock. */
public final class EolTable {

    public record Status(String status, String since, String note, String source) {
    }

    private record Entry(String match, String versionPrefix, Status status) {
    }

    private final String asOf;
    private final List<Entry> entries = new ArrayList<>();

    public static EolTable load(Path yaml) throws IOException {
        JsonNode root = new ObjectMapper(new YAMLFactory()).readTree(Files.readAllBytes(yaml));
        EolTable t = new EolTable(root.get("asOf").asText());
        for (JsonNode e : root.get("entries")) {
            t.entries.add(new Entry(e.get("match").asText(), e.path("versionPrefix").asText(""),
                    new Status(e.get("status").asText(), e.hasNonNull("since") ? e.get("since").asText() : null,
                            e.path("note").asText(), e.path("source").asText())));
        }
        return t;
    }

    private EolTable(String asOf) {
        this.asOf = asOf;
    }

    public String asOf() {
        return asOf;
    }

    public Status match(String groupId, String artifactId, String version) {
        String ga = groupId + ":" + artifactId;
        for (Entry e : entries) {
            boolean nameOk = e.match().endsWith("*") ? ga.startsWith(e.match().substring(0, e.match().length() - 1))
                    : ga.equals(e.match());
            if (nameOk && version.startsWith(e.versionPrefix())) {
                return e.status();
            }
        }
        return null;
    }
}
