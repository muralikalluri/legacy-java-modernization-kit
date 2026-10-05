package com.harborclaims.assess.deps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.harborclaims.assess.common.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** One committed file per query: Maven/{group}/{artifact}/{version}.json, so results regenerate without network. */
public final class OsvCache {

    public record Entry(String fetchedAt, JsonNode vulns) {
    }

    private final Path dir;

    public OsvCache(Path dir) {
        this.dir = dir;
    }

    Path fileFor(String g, String a, String v) {
        return dir.resolve("Maven").resolve(g).resolve(a).resolve(v + ".json");
    }

    public Optional<Entry> read(String g, String a, String v) throws IOException {
        Path f = fileFor(g, a, v);
        if (!Files.isRegularFile(f)) {
            return Optional.empty();
        }
        JsonNode n = Json.read(f);
        return Optional.of(new Entry(n.path("fetchedAt").asText(), n.path("vulns")));
    }

    public void write(String g, String a, String v, String fetchedAt, ArrayNode vulns) throws IOException {
        ObjectNode n = Json.MAPPER.createObjectNode();
        n.put("fetchedAt", fetchedAt);
        n.put("source", OsvClient.API);
        n.put("package", g + ":" + a);
        n.put("version", v);
        n.set("vulns", vulns);
        Json.write(fileFor(g, a, v), n);
    }
}
