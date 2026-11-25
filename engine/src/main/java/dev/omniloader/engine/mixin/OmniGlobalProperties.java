package dev.omniloader.engine.mixin;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Mixin's shared settings store. */
public final class OmniGlobalProperties implements IGlobalPropertyService {
    private record Key(String name) implements IPropertyKey {}
    private final Map<String, Key> keys = new ConcurrentHashMap<>();
    private final Map<IPropertyKey, Object> values = new ConcurrentHashMap<>();

    @Override public IPropertyKey resolveKey(String name) { return keys.computeIfAbsent(name, Key::new); }
    @Override @SuppressWarnings("unchecked") public <T> T getProperty(IPropertyKey key) { return (T) values.get(key); }
    @Override public void setProperty(IPropertyKey key, Object value) { if (value == null) values.remove(key); else values.put(key, value); }
    @Override @SuppressWarnings("unchecked") public <T> T getProperty(IPropertyKey key, T def) { return (T) values.getOrDefault(key, def); }
    @Override public String getPropertyString(IPropertyKey key, String def) { Object v = values.get(key); return v == null ? def : v.toString(); }
}
