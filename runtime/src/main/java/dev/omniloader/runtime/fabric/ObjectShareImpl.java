package dev.omniloader.runtime.fabric;

import net.fabricmc.loader.api.ObjectShare;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public final class ObjectShareImpl implements ObjectShare {
    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, List<BiConsumer<String, Object>>> waiting = new HashMap<>();

    public synchronized Object get(String key) { return values.get(key); }
    public void whenAvailable(String key, BiConsumer<String, Object> consumer) {
        Object v;
        synchronized (this) {
            v = values.get(key);
            if (v == null) { waiting.computeIfAbsent(key, k -> new ArrayList<>()).add(consumer); return; }
        }
        consumer.accept(key, v);
    }
    public Object put(String key, Object value) {
        Object prev; List<BiConsumer<String, Object>> w;
        synchronized (this) { prev = values.put(key, value); w = waiting.remove(key); }
        if (w != null) w.forEach(c -> c.accept(key, value));
        return prev;
    }
    public synchronized Object putIfAbsent(String key, Object value) {
        Object prev = values.get(key);
        if (prev == null) put(key, value);
        return prev;
    }
    public synchronized Object remove(String key) { return values.remove(key); }
}
