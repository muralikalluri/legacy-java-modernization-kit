package com.harborclaims.assess.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class JdepsRunnerTest {

    private static final String OUT = String.join("\n",
            "classes -> /abs/path/legacy-app/target/classes",
            "   com.harborclaims.portal.claims.ClaimManager        -> com.harborclaims.portal.policies.PolicyManager     classes",
            "   com.harborclaims.portal.claims.ClaimManager$FieldProblem -> com.harborclaims.portal.common.Fmt           classes",
            "   com.harborclaims.portal.claims.ClaimManager        -> com.harborclaims.portal.claims.ClaimManager$StatusRow classes",
            "   com.harborclaims.portal.claims.ClaimManager        -> java.lang.String                                   java.base",
            "   com.harborclaims.portal.claims.ClaimManager        -> org.apache.log4j.Logger                            not found",
            "   com.harborclaims.portal.claims.ClaimManager        -> com.harborclaims.portal.policies.PolicyManager     classes",
            "garbage line without arrows");

    @Test
    void keepsOnlyAppToAppEdgesFoldsInnerClassesAndDropsSelfEdges() {
        Set<String> edges = JdepsRunner.parse(OUT);
        assertEquals(Set.of(
                "com.harborclaims.portal.claims.ClaimManager -> com.harborclaims.portal.policies.PolicyManager",
                "com.harborclaims.portal.claims.ClaimManager -> com.harborclaims.portal.common.Fmt"), edges);
    }

    @Test
    void packageOfTakesEverythingBeforeTheClassName() {
        assertEquals("com.harborclaims.portal.claims", JdepsRunner.packageOf("com.harborclaims.portal.claims.ClaimManager"));
    }

    @Test
    void theCommandUsesTheJdkRunningThisProcessAndNoAbsolutePathsLeakIntoEdges() {
        assertTrue(JdepsRunner.command(java.nio.file.Path.of("x")).get(0).endsWith("jdeps"));
        assertTrue(JdepsRunner.parse(OUT).stream().noneMatch(e -> e.contains("/abs/")));
    }
}
