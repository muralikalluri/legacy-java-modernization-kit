package com.harborclaims.assess.estimate;

import com.harborclaims.assess.common.Hashing;
import com.harborclaims.assess.common.Json;
import com.harborclaims.assess.common.Modules;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "estimate", mixinStandardHelpOptions = true,
        description = "Apply the estimation model to the results: effort bands per phase and module, scores and sensitivity.")
public class EstimateCommand implements Callable<Integer> {

    @Option(names = "--model", description = "Estimation model (default: ${DEFAULT-VALUE})")
    Path model = Path.of("assess/estimation-model.yaml");

    @Option(names = "--modules", description = "Module map (default: ${DEFAULT-VALUE})")
    Path modules = Path.of("assess/modules.yaml");

    @Option(names = "--results", description = "Results directory (default: ${DEFAULT-VALUE})")
    Path results = Path.of("assess/results");

    @Option(names = "--out", description = "Output file (default: ${DEFAULT-VALUE})")
    Path out = Path.of("assess/results/estimate.json");

    @Override
    public Integer call() throws Exception {
        Model m = Model.load(model);
        Map<String, Object> built = EstimateBuilder.build(Json.read(results.resolve("inventory.json")),
                Json.read(results.resolve("structure.json")), Json.read(results.resolve("security.json")),
                Json.read(results.resolve("deps.json")), Modules.load(modules), m, Hashing.sha256(model), Hashing.sha256(modules));
        Json.write(out, built);
        System.out.println("estimate: wrote " + out + " totals=" + built.get("totals"));
        return 0;
    }
}
