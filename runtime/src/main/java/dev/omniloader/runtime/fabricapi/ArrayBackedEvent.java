package dev.omniloader.runtime.fabricapi;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Fabric-style event: listeners in an array, grouped into ordered "phases". */
public final class ArrayBackedEvent<T> extends Event<T> {
    private final Class<? super T> type;
    private final Function<T[], T> invokerFactory;
    private final Map<ResourceLocation, List<T>> phases = new LinkedHashMap<>();
    private final Map<ResourceLocation, Set<ResourceLocation>> after = new LinkedHashMap<>(); // phase -> phases that must run before it

    public ArrayBackedEvent(Class<? super T> type, Function<T[], T> invokerFactory) {
        this.type = type;
        this.invokerFactory = invokerFactory;
        phases.put(DEFAULT_PHASE, new ArrayList<>());
        rebuild();
    }

    @Override public void register(T listener) { register(DEFAULT_PHASE, listener); }

    @Override
    public synchronized void register(ResourceLocation phase, T listener) {
        if (listener == null) throw new NullPointerException("listener");
        phases.computeIfAbsent(phase, k -> new ArrayList<>()).add(listener);
        rebuild();
    }

    @Override
    public synchronized void addPhaseOrdering(ResourceLocation first, ResourceLocation second) {
        if (first.equals(second)) throw new IllegalArgumentException("a phase can't be ordered against itself");
        phases.computeIfAbsent(first, k -> new ArrayList<>());
        phases.computeIfAbsent(second, k -> new ArrayList<>());
        after.computeIfAbsent(second, k -> new HashSet<>()).add(first);
        rebuild();
    }

    @SuppressWarnings("unchecked")
    private void rebuild() {
        List<ResourceLocation> order = new ArrayList<>();
        Set<ResourceLocation> done = new HashSet<>(), visiting = new HashSet<>();
        for (ResourceLocation p : phases.keySet()) visit(p, order, done, visiting);
        List<T> all = new ArrayList<>();
        for (ResourceLocation p : order) all.addAll(phases.get(p));
        T[] arr = (T[]) Array.newInstance(type, all.size());
        invoker = invokerFactory.apply(all.toArray(arr));
    }

    private void visit(ResourceLocation p, List<ResourceLocation> order, Set<ResourceLocation> done, Set<ResourceLocation> visiting) {
        if (done.contains(p) || !visiting.add(p)) return; // cycles are ignored, like Fabric does
        for (ResourceLocation before : after.getOrDefault(p, Set.of())) visit(before, order, done, visiting);
        visiting.remove(p);
        done.add(p);
        order.add(p);
    }
}
