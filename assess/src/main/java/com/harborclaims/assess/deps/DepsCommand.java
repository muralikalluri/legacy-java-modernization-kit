package com.harborclaims.assess.deps;

import com.harborclaims.assess.common.Json;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "deps", mixinStandardHelpOptions = true,
        description = "Identify the WAR's libraries, look up advisories in OSV (cached) and flag end-of-life frameworks.")
public class DepsCommand implements Callable<Integer> {

    @Option(names = "--war", description = "WAR to scan (default: ${DEFAULT-VALUE})")
    Path war = Path.of("legacy-app/target/portal.war");

    @Option(names = "--pom", description = "Legacy pom, for provided-scope dependencies (default: ${DEFAULT-VALUE})")
    Path pom = Path.of("legacy-app/pom.xml");

    @Option(names = "--cache", description = "OSV cache directory (default: ${DEFAULT-VALUE})")
    Path cache = Path.of("assess/osv-cache");

    @Option(names = "--reference", description = "Reference data directory (default: ${DEFAULT-VALUE})")
    Path reference = Path.of("assess/reference");

    @Option(names = "--out", description = "Output file (default: ${DEFAULT-VALUE})")
    Path out = Path.of("assess/results/deps.json");

    @Option(names = "--offline", description = "Use only the cache. Never touches the network; a miss is reported as UNKNOWN.")
    boolean offline;

    @Option(names = "--refresh", description = "Query OSV for every library and rewrite the cache.")
    boolean refresh;

    @Override
    public Integer call() throws Exception {
        if (offline && refresh) {
            System.err.println("deps: --offline and --refresh cannot be combined");
            return 2;
        }
        DepsBuilder.Mode mode = offline ? DepsBuilder.Mode.OFFLINE : refresh ? DepsBuilder.Mode.REFRESH : DepsBuilder.Mode.AUTO;
        DepsBuilder builder = new DepsBuilder(new OsvCache(cache), new OsvClient(new JavaHttpFetcher()), mode,
                DepsBuilder.realToday());
        Map<String, Object> result = builder.build(war, pom, reference);
        Json.write(out, result);
        System.out.println("deps: wrote " + out + " " + result.get("summary"));
        return 0;
    }
}
