package com.harborclaims.assess.structure;

import com.harborclaims.assess.common.Hashing;
import com.harborclaims.assess.common.Json;
import com.harborclaims.assess.common.Modules;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "structure", mixinStandardHelpOptions = true,
        description = "Package and module dependency graph (jdeps), cycles, fan-in/out and the largest classes.")
public class StructureCommand implements Callable<Integer> {

    @Option(names = "--classes", description = "Compiled classes (default: ${DEFAULT-VALUE})")
    Path classes = Path.of("legacy-app/target/classes");

    @Option(names = "--source", description = "Legacy app directory (default: ${DEFAULT-VALUE})")
    Path source = Path.of("legacy-app");

    @Option(names = "--inventory", description = "inventory.json from `assess inventory` (default: ${DEFAULT-VALUE})")
    Path inventory = Path.of("assess/results/inventory.json");

    @Option(names = "--modules", description = "Module map (default: ${DEFAULT-VALUE})")
    Path modules = Path.of("assess/modules.yaml");

    @Option(names = "--out", description = "Output file (default: ${DEFAULT-VALUE})")
    Path out = Path.of("assess/results/structure.json");

    @Override
    public Integer call() throws Exception {
        Set<String> classEdges = JdepsRunner.run(classes);
        Set<String> sourceEdges = SourceImports.packageEdges(source.resolve("src/main/java"));
        Map<String, Object> result = StructureBuilder.build(Json.read(inventory), classEdges, sourceEdges,
                Modules.load(modules), Hashing.sha256(modules));
        Json.write(out, result);
        System.out.println("structure: wrote " + out + " " + result.get("summary"));
        return 0;
    }
}
