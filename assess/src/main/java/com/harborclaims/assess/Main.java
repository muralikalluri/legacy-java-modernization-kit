package com.harborclaims.assess;

import com.harborclaims.assess.deps.DepsCommand;
import com.harborclaims.assess.estimate.EstimateCommand;
import com.harborclaims.assess.inventory.InventoryCommand;
import com.harborclaims.assess.jakarta.JakartaCommand;
import com.harborclaims.assess.report.ReportCommand;
import com.harborclaims.assess.security.SecurityCommand;
import com.harborclaims.assess.structure.StructureCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "assess", mixinStandardHelpOptions = true, version = "assess 1.0.0",
        description = "Modernization assessment toolkit for the Harbor Claims Portal.",
        subcommands = {InventoryCommand.class, DepsCommand.class, JakartaCommand.class, StructureCommand.class, SecurityCommand.class, EstimateCommand.class, ReportCommand.class})
public class Main implements Runnable {

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).setCaseInsensitiveEnumValuesAllowed(true).execute(args));
    }

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}
