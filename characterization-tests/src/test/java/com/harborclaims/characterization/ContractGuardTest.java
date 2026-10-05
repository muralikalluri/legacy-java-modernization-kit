package com.harborclaims.characterization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The tests must run unchanged against legacy and modern. Only KnownDivergencesTest may look at the target; nothing may be
 * skipped, disabled or made conditional anywhere else. These words are built from parts so this file does not match itself.
 */
class ContractGuardTest {

    private static final List<String> FORBIDDEN = List.of("Target" + ".kind(", "Target" + ".isLegacy(", "isLegacy" + "(", "Kind" + ".MODERN",
            "Kind" + ".LEGACY", "Assumptions" + ".", "@Dis" + "abled", "@Enabled" + "If", "@Enabled" + "On", "@Disabled" + "If", "@Tag" + "(",
            "System" + ".getProperty", "System" + ".getenv", "assume" + "True", "assume" + "That");

    @Test
    void noTestOutsideTheDivergenceClassDependsOnTheTarget() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/test/java"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = p.getFileName().toString();
                if (name.equals("KnownDivergencesTest.java") || name.equals("Target.java") || name.equals("ContractGuardTest.java")) {
                    continue;
                }
                String text = Files.readString(p);
                for (String word : FORBIDDEN) {
                    if (text.contains(word)) {
                        offenders.add(name + " uses " + word);
                    }
                }
            }
        }
        assertEquals(List.of(), offenders, "tests must behave identically on every target");
    }

    @Test
    void everyDivergenceBranchesOnTheTargetSoItPinsBothSides() throws IOException {
        String text = Files.readString(Path.of("src/test/java/com/harborclaims/characterization/KnownDivergencesTest.java"));
        String[] chunks = text.split("void divergence");
        assertEquals(8, chunks.length, "seven divergence methods");
        for (int i = 1; i < chunks.length; i++) {
            String body = chunks[i].contains("@Test") ? chunks[i].substring(0, chunks[i].indexOf("@Test")) : chunks[i];
            assertTrue(body.contains("Target.isLegacy()"), "divergence " + i + " must pin an expectation for both targets");
            assertTrue(body.contains("else") || body.contains(": "), "divergence " + i + " must state the other side too");
        }
        assertTrue(!text.contains("@Disabled"));
    }
}
