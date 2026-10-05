package com.harborclaims.characterization;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** One browser session: its own cookie jar, redirects never followed (a 302 is a result worth asserting). */
public final class Session {

    /** A response reduced to what the tests may assert on. */
    public record Reply(int status, String location, String body, String contentType) {

        public Document html() {
            return Jsoup.parse(body);
        }

        /** Path and query of the Location header only: the host and port differ behind a proxy. */
        public String redirectPath() {
            if (location == null) {
                return null;
            }
            URI u = URI.create(location);
            if (u.isAbsolute()) {
                URI base = URI.create(Target.baseUrl());
                int basePort = base.getPort() == -1 ? ("https".equals(base.getScheme()) ? 443 : 80) : base.getPort();
                int port = u.getPort() == -1 ? ("https".equals(u.getScheme()) ? 443 : 80) : u.getPort();
                if (!base.getScheme().equals(u.getScheme()) || !base.getHost().equals(u.getHost()) || basePort != port) {
                    throw new AssertionError("Location points at another origin than the one under test: " + location
                            + " (expected " + Target.baseUrl() + "). A proxy must pass the original Host header, including the port.");
                }
            }
            String path = u.getRawPath() == null ? "" : u.getRawPath();
            return u.getRawQuery() == null ? path : path + "?" + u.getRawQuery();
        }
    }

    private final HttpClient client;

    private Session() {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.client = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** A session that has not signed in. */
    public static Session anonymous() {
        return new Session();
    }

    /** A signed-in session. Seeded demo users have the password "username-demo". */
    public static Session login(String username) {
        Session s = new Session();
        Reply r = s.post("/loginSubmit.do", Map.of("username", username, "password", username + "-demo"));
        if (r.status() != 302 || !"/home.do".equals(r.redirectPath())) {
            throw new IllegalStateException("login as " + username + " failed: HTTP " + r.status() + " Location " + r.location());
        }
        return s;
    }

    public Reply get(String pathAndQuery) {
        return send(HttpRequest.newBuilder(URI.create(Target.baseUrl() + pathAndQuery)).timeout(Duration.ofSeconds(30))
                .header("Accept", "text/html").header("Accept-Language", "en-US").GET().build());
    }

    public Reply post(String path, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return send(HttpRequest.newBuilder(URI.create(Target.baseUrl() + path)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "text/html")
                .header("Accept-Language", "en-US").POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    /** Builds a query string with every value URL-encoded. */
    public static String query(String... keyValues) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put(keyValues[i], keyValues[i + 1]);
        }
        return m.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private Reply send(HttpRequest req) {
        try {
            HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Reply(r.statusCode(), r.headers().firstValue("Location").orElse(null), r.body(),
                    r.headers().firstValue("Content-Type").orElse(""));
        } catch (IOException e) {
            throw new IllegalStateException("request failed: " + req.uri() + " (" + e.getMessage() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
