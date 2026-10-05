package com.harborclaims.assess.inventory;

import com.harborclaims.assess.common.Json;
import com.harborclaims.assess.common.Modules;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "inventory", mixinStandardHelpOptions = true,
        description = "Inventory the legacy app: actions, forms, forwards, filters, JSPs, scriptlets, roles per action.")
public class InventoryCommand implements Callable<Integer> {

    @Option(names = "--source", description = "Legacy app directory (default: ${DEFAULT-VALUE})")
    Path source = Path.of("legacy-app");

    @Option(names = "--modules", description = "Module map (default: ${DEFAULT-VALUE})")
    Path modules = Path.of("assess/modules.yaml");

    @Option(names = "--out", description = "Output file (default: ${DEFAULT-VALUE})")
    Path out = Path.of("assess/results/inventory.json");

    @Override
    public Integer call() throws Exception {
        Map<String, Object> inventory = InventoryBuilder.build(source, Modules.load(modules));
        Json.write(out, inventory);
        Object summary = inventory.get("summary");
        System.out.println("inventory: wrote " + out + " " + summary);
        return 0;
    }
}
