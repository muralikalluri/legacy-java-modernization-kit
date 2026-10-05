package com.harborclaims.assess.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.harborclaims.assess.common.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "report", mixinStandardHelpOptions = true,
        description = "Render the quick and the full assessment from the results through templates, and write the risk register.")
public class ReportCommand implements Callable<Integer> {

    @Option(names = "--results", description = "Results directory (default: ${DEFAULT-VALUE})")
    Path results = Path.of("assess/results");

    @Option(names = "--risk-rules", description = "Risk rules (default: ${DEFAULT-VALUE})")
    Path riskRules = Path.of("assess/reference/risk-rules.yaml");

    @Option(names = "--template", description = "Template (default: ${DEFAULT-VALUE})")
    Path template = Path.of("assess/templates/quick-assessment.md.mustache");

    @Option(names = "--out", description = "Report file (default: ${DEFAULT-VALUE})")
    Path out = Path.of("sample-deliverable/QUICK_ASSESSMENT.md");

    @Option(names = "--full-template", description = "Full report template (default: ${DEFAULT-VALUE})")
    Path fullTemplate = Path.of("assess/templates/full-assessment.md.mustache");

    @Option(names = "--mapping", description = "Framework mapping (default: ${DEFAULT-VALUE})")
    Path mapping = Path.of("assess/reference/framework-mapping.yaml");

    @Option(names = "--full-out", description = "Full report file (default: ${DEFAULT-VALUE})")
    Path fullOut = Path.of("sample-deliverable/ASSESSMENT_AND_ROADMAP.md");

    @Option(names = "--readme", description = "README whose results block is regenerated (default: ${DEFAULT-VALUE})")
    Path readme = Path.of("README.md");

    @Option(names = "--risks-out", description = "Risk register file (default: ${DEFAULT-VALUE})")
    Path risksOut = Path.of("assess/results/risks.json");

    @Override
    public Integer call() throws Exception {
        JsonNode inventory = Json.read(results.resolve("inventory.json"));
        JsonNode deps = Json.read(results.resolve("deps.json"));
        JsonNode jakarta = Json.read(results.resolve("jakarta.json"));
        JsonNode structure = Json.read(results.resolve("structure.json"));
        JsonNode security = Json.read(results.resolve("security.json"));
        JsonNode estimate = Json.read(results.resolve("estimate.json"));
        ReportBuilder.Output o = ReportBuilder.build(Files.readString(template, StandardCharsets.UTF_8), RiskRules.load(riskRules),
                inventory, deps, jakarta, structure, security, estimate);
        String full = FullReportBuilder.build(Files.readString(fullTemplate, StandardCharsets.UTF_8), o.viewModel(), o.risksJson(),
                FullReportBuilder.loadMapping(mapping), inventory, deps, jakarta, structure, security, estimate);
        Files.createDirectories(fullOut.toAbsolutePath().getParent());
        Files.write(fullOut, full.getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.write(out, o.markdown().getBytes(StandardCharsets.UTF_8));
        String block = FullReportBuilder.readmeResults(inventory, deps, jakarta, structure, security, estimate);
        Files.write(readme, FullReportBuilder.replaceReadmeBlock(Files.readString(readme, StandardCharsets.UTF_8), block).getBytes(StandardCharsets.UTF_8));
        Json.write(risksOut, o.risksJson());
        System.out.println("report: wrote " + out + ", " + fullOut + " and " + risksOut);
        return 0;
    }
}
