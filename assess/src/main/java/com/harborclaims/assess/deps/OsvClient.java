package com.harborclaims.assess.deps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.harborclaims.assess.common.Json;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Queries the OSV API for one Maven coordinate. OSV does the version matching; we never re-match ranges. */
public final class OsvClient {

    public static final String API = "https://api.osv.dev/v1/query";

    private final HttpFetcher http;
    private final long[] backoffMillis;

    public OsvClient(HttpFetcher http) {
        this(http, new long[] {1000, 2000, 4000});
    }

    public OsvClient(HttpFetcher http, long[] backoffMillis) {
        this.http = http;
        this.backoffMillis = backoffMillis;
    }

    /** Returns all vulnerability records for the coordinate, sorted by id, following next_page_token. */
    public ArrayNode query(String groupId, String artifactId, String version) throws IOException {
        List<JsonNode> all = new ArrayList<>();
        String token = null;
        do {
            ObjectNode body = Json.MAPPER.createObjectNode();
            ObjectNode pkg = body.putObject("package");
            pkg.put("name", groupId + ":" + artifactId);
            pkg.put("ecosystem", "Maven");
            body.put("version", version);
            if (token != null) {
                body.put("page_token", token);
            }
            JsonNode page = Json.MAPPER.readTree(postWithRetry(body.toString()));
            page.path("vulns").forEach(all::add);
            token = page.hasNonNull("next_page_token") ? page.get("next_page_token").asText() : null;
        } while (token != null);
        all.sort(Comparator.comparing(v -> v.path("id").asText()));
        ArrayNode out = Json.MAPPER.createArrayNode();
        all.forEach(out::add);
        return out;
    }

    private String postWithRetry(String body) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt <= backoffMillis.length; attempt++) {
            try {
                HttpFetcher.Response r = http.post(API, body);
                if (r.status() == 200) {
                    return r.body();
                }
                boolean retryable = r.status() == 429 || r.status() >= 500;
                if (!retryable) {
                    throw new IOException("OSV returned HTTP " + r.status());
                }
                last = new IOException("OSV returned HTTP " + r.status());
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().startsWith("OSV returned HTTP 4")) {
                    throw e;
                }
                last = e;
            }
            if (attempt < backoffMillis.length) {
                try {
                    Thread.sleep(backoffMillis[attempt]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted", ie);
                }
            }
        }
        throw new IOException("OSV query failed after retries: " + (last == null ? "" : last.getMessage()));
    }
}
