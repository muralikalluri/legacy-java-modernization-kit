package com.harborclaims.assess.structure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Small deterministic graph helpers: strongly connected components and a readable cycle path. */
public final class Graphs {

    private Graphs() {
    }

    /** Adjacency with every node present and neighbours sorted. */
    public static Map<String, Set<String>> adjacency(Iterable<String> nodes, Iterable<String[]> edges) {
        Map<String, Set<String>> adj = new TreeMap<>();
        nodes.forEach(n -> adj.put(n, new TreeSet<>()));
        for (String[] e : edges) {
            adj.computeIfAbsent(e[0], k -> new TreeSet<>()).add(e[1]);
            adj.computeIfAbsent(e[1], k -> new TreeSet<>());
        }
        return adj;
    }

    /** Strongly connected components with more than one node (self loops are ignored), each sorted, list sorted. */
    public static List<List<String>> cycles(Map<String, Set<String>> adj) {
        Tarjan t = new Tarjan(adj);
        List<List<String>> out = new ArrayList<>();
        for (String n : adj.keySet()) {
            if (!t.index.containsKey(n)) {
                t.visit(n);
            }
        }
        for (List<String> scc : t.sccs) {
            if (scc.size() > 1) {
                Collections.sort(scc);
                out.add(scc);
            }
        }
        out.sort((a, b) -> a.get(0).compareTo(b.get(0)));
        return out;
    }

    /** The shortest cycle through the smallest member of the component, as "a -> b -> a". */
    public static List<String> cyclePath(Map<String, Set<String>> adj, List<String> component) {
        String start = component.get(0);
        Set<String> members = new TreeSet<>(component);
        Map<String, String> parent = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(start);
        parent.put(start, null);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (String next : adj.getOrDefault(cur, Set.of())) {
                if (!members.contains(next)) {
                    continue;
                }
                if (next.equals(start)) {
                    List<String> path = new ArrayList<>();
                    for (String n = cur; n != null; n = parent.get(n)) {
                        path.add(0, n);
                    }
                    path.add(start);
                    return path;
                }
                if (!parent.containsKey(next)) {
                    parent.put(next, cur);
                    queue.add(next);
                }
            }
        }
        return List.of(start);
    }

    private static final class Tarjan {
        final Map<String, Set<String>> adj;
        final Map<String, Integer> index = new HashMap<>();
        final Map<String, Integer> low = new HashMap<>();
        final Deque<String> stack = new ArrayDeque<>();
        final Set<String> onStack = new TreeSet<>();
        final List<List<String>> sccs = new ArrayList<>();
        int counter;

        Tarjan(Map<String, Set<String>> adj) {
            this.adj = adj;
        }

        void visit(String v) {
            index.put(v, counter);
            low.put(v, counter);
            counter++;
            stack.push(v);
            onStack.add(v);
            for (String w : adj.getOrDefault(v, Set.of())) {
                if (w.equals(v)) {
                    continue;
                }
                if (!index.containsKey(w)) {
                    visit(w);
                    low.put(v, Math.min(low.get(v), low.get(w)));
                } else if (onStack.contains(w)) {
                    low.put(v, Math.min(low.get(v), index.get(w)));
                }
            }
            if (low.get(v).equals(index.get(v))) {
                List<String> scc = new ArrayList<>();
                String w;
                do {
                    w = stack.pop();
                    onStack.remove(w);
                    scc.add(w);
                } while (!w.equals(v));
                sccs.add(scc);
            }
        }
    }
}
