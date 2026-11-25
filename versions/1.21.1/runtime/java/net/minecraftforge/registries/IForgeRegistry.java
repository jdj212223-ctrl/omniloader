package net.minecraftforge.registries;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Forge's registry view (Omniloader stand-in, backed by the game's own registries on NeoForge). */
public interface IForgeRegistry<V> extends Iterable<V> {
    ResourceKey<Registry<V>> getRegistryKey();
    ResourceLocation getRegistryName();
    void register(String key, V value);
    void register(ResourceLocation key, V value);
    boolean containsKey(ResourceLocation key);
    boolean containsValue(V value);
    boolean isEmpty();
    V getValue(ResourceLocation key);
    ResourceLocation getKey(V value);
    Optional<ResourceKey<V>> getResourceKey(V value);
    Set<ResourceLocation> getKeys();
    Collection<V> getValues();
    Set<Map.Entry<ResourceKey<V>, V>> getEntries();
    Optional<Holder<V>> getHolder(ResourceKey<V> key);
    Optional<Holder<V>> getHolder(ResourceLocation location);
    Optional<Holder<V>> getHolder(V value);
    /** Omniloader: the game registry behind this view. */
    Registry<V> vanilla();
}
