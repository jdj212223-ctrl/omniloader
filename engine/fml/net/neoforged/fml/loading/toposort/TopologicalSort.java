package net.neoforged.fml.loading.toposort;
import com.google.common.graph.Graph;
import java.util.*;
public final class TopologicalSort {
    public static <T> List<T> topologicalSort(Graph<T> graph, Comparator<? super T> comparator) throws IllegalArgumentException {
        Map<T, Integer> in = new HashMap<>();
        for (T n : graph.nodes()) in.put(n, graph.inDegree(n));
        PriorityQueue<T> q = new PriorityQueue<>(comparator == null ? (a, b) -> 0 : comparator);
        in.forEach((n, d) -> { if (d == 0) q.add(n); });
        List<T> out = new ArrayList<>();
        while (!q.isEmpty()) {
            T n = q.poll(); out.add(n);
            for (T s : graph.successors(n)) if (in.merge(s, -1, Integer::sum) == 0) q.add(s);
        }
        if (out.size() != graph.nodes().size()) throw new IllegalArgumentException("Cycle in sort graph");
        return out;
    }
}
