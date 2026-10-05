package com.harborclaims.assess.report;

import com.harborclaims.assess.common.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "report", mixinStandardHelpOptions = true,
        description = "Render the quick assessment from the results through a template, and write the risk register.")
public class ReportCommand implements Callable<Integer> {

    @Option(names = "--results", description = "Results directory (default: ${DEFAULT-VALUE})")
    Path results = Path.of("assess/results");

    @Option(names = "--risk-rules", description = "Risk rules (default: ${DEFAULT-VALUE})")
    Path riskRules = Path.of("assess/reference/risk-rules.yaml");

    @Option(names = "--template", description = "Template (default: ${DEFAULT-VALUE})")
    Path template = Path.of("assess/templates/quick-assessment.md.mustache");

    @Option(names = "--out", description = "Report file (default: ${DEFAULT-VALUE})")
    Path out = Path.of("sample-deliverable/QUICK_ASSESSMENT.md");

    @Option(names = "--risks-out", description = "Risk register file (default: ${DEFAULT-VALUE})")
    Path risksOut = Path.of("assess/results/risks.json");

    @Override
    public Integer call() throws Exception {
        ReportBuilder.Output o = ReportBuilder.build(Files.readString(template, StandardCharsets.UTF_8), RiskRules.load(riskRules),
                Json.read(results.resolve("inventory.json")), Json.read(results.resolve("deps.json")),
                Json.read(results.resolve("jakarta.json")), Json.read(results.resolve("structure.json")),
                Json.read(results.resolve("security.json")), Json.read(results.resolve("estimate.json")));
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.write(out, o.markdown().getBytes(StandardCharsets.UTF_8));
        Json.write(risksOut, o.risksJson());
        System.out.println("report: wrote " + out + " and " + risksOut);
        return 0;
    }
}
