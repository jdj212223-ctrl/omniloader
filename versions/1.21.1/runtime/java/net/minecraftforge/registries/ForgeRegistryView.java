package net.minecraftforge.registries;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModLoadingContext;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Omniloader: a Forge registry view over a game registry. */
public final class ForgeRegistryView<V> implements IForgeRegistry<V> {
    private final Supplier<Registry<V>> registry;

    public ForgeRegistryView(Supplier<Registry<V>> registry) { this.registry = registry; }

    public Registry<V> vanilla() { return registry.get(); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ResourceKey<Registry<V>> getRegistryKey() { return (ResourceKey) vanilla().key(); }
    public ResourceLocation getRegistryName() { return vanilla().key().location(); }
    public void register(String key, V value) { register(ResourceLocation.fromNamespaceAndPath(ModLoadingContext.get().getActiveNamespace(), key), value); }
    public void register(ResourceLocation key, V value) { Registry.register(vanilla(), key, value); }
    public boolean containsKey(ResourceLocation key) { return vanilla().containsKey(key); }
    public boolean containsValue(V value) { return vanilla().getKey(value) != null; }
    public boolean isEmpty() { return vanilla().size() == 0; }
    public V getValue(ResourceLocation key) { return vanilla().get(key); }
    public ResourceLocation getKey(V value) { return vanilla().getKey(value); }
    public Optional<ResourceKey<V>> getResourceKey(V value) { return vanilla().getResourceKey(value); }
    public Set<ResourceLocation> getKeys() { return vanilla().keySet(); }
    public Collection<V> getValues() { return vanilla().stream().toList(); }
    public Set<Map.Entry<ResourceKey<V>, V>> getEntries() { return vanilla().entrySet(); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<Holder<V>> getHolder(ResourceKey<V> key) { return (Optional) vanilla().getHolder(key); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<Holder<V>> getHolder(ResourceLocation location) { return (Optional) vanilla().getHolder(location); }
    public Optional<Holder<V>> getHolder(V value) { return getResourceKey(value).flatMap(this::getHolder); }
    public Iterator<V> iterator() { return vanilla().iterator(); }
}
