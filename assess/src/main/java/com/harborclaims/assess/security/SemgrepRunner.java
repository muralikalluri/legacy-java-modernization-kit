package com.harborclaims.assess.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.harborclaims.assess.common.Json;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Runs Semgrep offline with the local ruleset and reduces the output to rule id, file, line and message. */
public final class SemgrepRunner {

    public record Raw(String ruleId, String file, int line, String message) {
    }

    public record Result(String version, List<Raw> findings, int errors) {
    }

    private SemgrepRunner() {
    }

    public static boolean available() {
        try {
            Process p = new ProcessBuilder("semgrep", "--version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static List<String> command(Path rules, List<Path> targets) {
        List<String> cmd = new ArrayList<>(List.of("semgrep", "--config", rules.toString(), "--metrics=off", "--disable-version-check",
                "--quiet", "--no-rewrite-rule-ids", "--json"));
        targets.forEach(t -> cmd.add(t.toString()));
        return cmd;
    }

    public static String version() throws IOException, InterruptedException {
        Process p = new ProcessBuilder("semgrep", "--version", "--disable-version-check").redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).trim();
        p.waitFor();
        String[] lines = out.split("\n");
        return lines[lines.length - 1].trim();
    }

    /** {@code stripPrefix} (for example "legacy-app/") is removed from reported paths so they are relative to the app. */
    public static Result run(Path rules, Path target, String stripPrefix) throws IOException, InterruptedException {
        return run(rules, List.of(target), stripPrefix);
    }

    /** Several explicit targets. Files named explicitly are never skipped by Semgrep's default ignore patterns. */
    public static Result run(Path rules, List<Path> targets, String stripPrefix) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command(rules, targets)).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String out = new String(p.getInputStream().readAllBytes());
        int exit = p.waitFor();
        if (exit != 0 && exit != 1) {
            throw new IOException("semgrep failed with exit " + exit);
        }
        return parse(out, stripPrefix, version());
    }

    public static Result parse(String json, String stripPrefix, String version) throws IOException {
        JsonNode root = Json.MAPPER.readTree(json);
        List<Raw> findings = new ArrayList<>();
        for (JsonNode r : root.path("results")) {
            String id = r.path("check_id").asText();
            int at = id.indexOf("harbor.");
            if (at > 0) {
                id = id.substring(at);
            }
            String file = r.path("path").asText();
            if (stripPrefix != null && file.startsWith(stripPrefix)) {
                file = file.substring(stripPrefix.length());
            }
            findings.add(new Raw(id, file, r.path("start").path("line").asInt(), r.path("extra").path("message").asText()));
        }
        findings.sort(Comparator.comparing(Raw::ruleId).thenComparing(Raw::file).thenComparingInt(Raw::line));
        return new Result(version, findings, root.path("errors").size());
    }
}
