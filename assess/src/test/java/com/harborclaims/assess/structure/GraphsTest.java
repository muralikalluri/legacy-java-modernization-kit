package com.harborclaims.assess.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphsTest {

    private static Map<String, Set<String>> graph(String... edges) {
        java.util.TreeSet<String> nodes = new java.util.TreeSet<>();
        List<String[]> list = new java.util.ArrayList<>();
        for (String e : edges) {
            String[] p = e.split(">");
            nodes.add(p[0]);
            nodes.add(p[1]);
            list.add(p);
        }
        return Graphs.adjacency(nodes, list);
    }

    @Test
    void aDagHasNoCycles() {
        assertEquals(List.of(), Graphs.cycles(graph("a>b", "b>c", "a>c")));
    }

    @Test
    void selfLoopsAreNotCycles() {
        assertEquals(List.of(), Graphs.cycles(graph("a>a", "a>b")));
    }

    @Test
    void aTwoCycleIsFoundWithItsPath() {
        Map<String, Set<String>> g = graph("a>b", "b>a", "b>c");
        List<List<String>> cycles = Graphs.cycles(g);
        assertEquals(List.of(List.of("a", "b")), cycles);
        assertEquals(List.of("a", "b", "a"), Graphs.cyclePath(g, cycles.get(0)));
    }

    @Test
    void aThreeNodeComponentIsOneCycleNotThree() {
        Map<String, Set<String>> g = graph("claims>policies", "policies>claims", "customers>policies", "policies>customers");
        List<List<String>> cycles = Graphs.cycles(g);
        assertEquals(1, cycles.size());
        assertEquals(List.of("claims", "customers", "policies"), cycles.get(0));
    }

    @Test
    void separateComponentsAreSortedBySmallestMember() {
        List<List<String>> cycles = Graphs.cycles(graph("x>y", "y>x", "a>b", "b>a"));
        assertEquals(List.of(List.of("a", "b"), List.of("x", "y")), cycles);
    }

    @Test
    void theCyclePathIsAShortestCycleThroughTheSmallestMember() {
        Map<String, Set<String>> g = graph("a>b", "b>c", "c>a", "a>c");
        List<String> path = Graphs.cyclePath(g, Graphs.cycles(g).get(0));
        assertEquals("a", path.get(0));
        assertEquals("a", path.get(path.size() - 1));
        assertTrue(path.size() <= 4);
    }

    @Test
    void shuffledEdgeOrderGivesTheSameAnswer() {
        List<List<String>> a = Graphs.cycles(graph("a>b", "b>c", "c>a", "d>e"));
        List<List<String>> b = Graphs.cycles(graph("d>e", "c>a", "b>c", "a>b"));
        assertEquals(a, b);
    }
}
