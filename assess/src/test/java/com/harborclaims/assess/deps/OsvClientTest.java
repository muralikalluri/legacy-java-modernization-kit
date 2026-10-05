package com.harborclaims.assess.deps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.harborclaims.assess.common.Json;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OsvClientTest {

    @Test
    void followsNextPageTokenAndSortsById() throws Exception {
        List<String> bodies = new ArrayList<>();
        HttpFetcher http = (url, body) -> {
            bodies.add(body);
            return bodies.size() == 1
                    ? new HttpFetcher.Response(200, "{\"vulns\":[{\"id\":\"B\"}],\"next_page_token\":\"t2\"}")
                    : new HttpFetcher.Response(200, "{\"vulns\":[{\"id\":\"A\"}]}");
        };
        ArrayNode out = new OsvClient(http, new long[] {0}).query("g", "a", "1.0");
        assertEquals(2, bodies.size());
        JsonNode second = Json.MAPPER.readTree(bodies.get(1));
        assertEquals("t2", second.get("page_token").asText());
        assertEquals("g:a", second.get("package").get("name").asText());
        assertEquals("Maven", second.get("package").get("ecosystem").asText());
        assertEquals("A", out.get(0).get("id").asText());
        assertEquals("B", out.get(1).get("id").asText());
    }

    @Test
    void retriesAfter429ThenSucceeds() throws Exception {
        int[] calls = {0};
        HttpFetcher http = (url, body) -> ++calls[0] == 1 ? new HttpFetcher.Response(429, "slow down")
                : new HttpFetcher.Response(200, "{\"vulns\":[]}");
        assertEquals(0, new OsvClient(http, new long[] {0, 0}).query("g", "a", "1").size());
        assertEquals(2, calls[0]);
    }

    @Test
    void persistent500FailsAfterTheConfiguredRetries() {
        int[] calls = {0};
        HttpFetcher http = (url, body) -> {
            calls[0]++;
            return new HttpFetcher.Response(500, "down");
        };
        assertThrows(IOException.class, () -> new OsvClient(http, new long[] {0, 0}).query("g", "a", "1"));
        assertEquals(3, calls[0], "first try plus two retries");
    }

    @Test
    void aClientErrorIsNotRetried() {
        int[] calls = {0};
        HttpFetcher http = (url, body) -> {
            calls[0]++;
            return new HttpFetcher.Response(400, "bad request");
        };
        assertThrows(IOException.class, () -> new OsvClient(http, new long[] {0, 0}).query("g", "a", "1"));
        assertEquals(1, calls[0]);
    }

    @Test
    void networkErrorsAreRetried() throws Exception {
        int[] calls = {0};
        HttpFetcher http = (url, body) -> {
            if (++calls[0] == 1) {
                throw new IOException("connection reset");
            }
            return new HttpFetcher.Response(200, "{}");
        };
        assertEquals(0, new OsvClient(http, new long[] {0}).query("g", "a", "1").size());
    }
}
