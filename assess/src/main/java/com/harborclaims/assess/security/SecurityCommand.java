package com.harborclaims.assess.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.harborclaims.assess.common.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "security", mixinStandardHelpOptions = true,
        description = "Run the local Semgrep ruleset and cross-reference with the inventory (missing role checks).")
public class SecurityCommand implements Callable<Integer> {

    public enum Semgrep {
        /** Run Semgrep (must be installed). */
        RUN,
        /** Reuse the committed Semgrep block, but only if the sources and rules are unchanged. */
        REUSE
    }

    @Option(names = "--source", description = "Legacy app directory (default: ${DEFAULT-VALUE})")
    Path source = Path.of("legacy-app");

    @Option(names = "--rules", description = "Semgrep rules (default: ${DEFAULT-VALUE})")
    Path rules = Path.of("assess/rules");

    @Option(names = "--inventory", description = "inventory.json (default: ${DEFAULT-VALUE})")
    Path inventory = Path.of("assess/results/inventory.json");

    @Option(names = "--out", description = "Output file (default: ${DEFAULT-VALUE})")
    Path out = Path.of("assess/results/security.json");

    @Option(names = "--semgrep", description = "run or reuse (default: ${DEFAULT-VALUE})")
    Semgrep mode = Semgrep.RUN;

    @Override
    public Integer call() throws Exception {
        String fingerprint = SecurityBuilder.semgrepInputsFingerprint(source, rules);
        SemgrepRunner.Result result = mode == Semgrep.RUN ? run() : reuse(fingerprint);
        int tokens = SecurityBuilder.tokenChecks(source.resolve("src/main/java"));
        Map<String, Object> built = SecurityBuilder.build(Json.read(inventory), result, tokens, fingerprint);
        Json.write(out, built);
        System.out.println("security: wrote " + out + " " + built.get("summary"));
        return 0;
    }

    private SemgrepRunner.Result run() throws IOException, InterruptedException {
        if (!SemgrepRunner.available()) {
            throw new IOException("semgrep is not installed. Install it, or use --semgrep reuse to keep the committed result.");
        }
        String prefix = source.toString().replace('\\', '/') + "/";
        return SemgrepRunner.run(rules, source.resolve("src/main"), prefix);
    }

    private SemgrepRunner.Result reuse(String fingerprint) throws IOException {
        if (!Files.isRegularFile(out)) {
            throw new IOException("--semgrep reuse needs an existing " + out);
        }
        JsonNode sg = Json.read(out).path("semgrep");
        if (!fingerprint.equals(sg.path("inputsFingerprint").asText())) {
            throw new IOException("The committed Semgrep result is stale: sources or rules changed since it was produced. "
                    + "Install semgrep and run without --semgrep reuse.");
        }
        List<SemgrepRunner.Raw> raws = new ArrayList<>(Json.MAPPER.convertValue(sg.path("rawFindings"),
                new TypeReference<List<SemgrepRunner.Raw>>() {
                }));
        return new SemgrepRunner.Result(sg.path("version").asText(), raws, 0);
    }
}
