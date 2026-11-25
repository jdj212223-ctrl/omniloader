package net.minecraftforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Forge's DeferredRegister (Omniloader stand-in over NeoForge's DeferredRegister). */
public final class DeferredRegister<T> {
    private final net.neoforged.neoforge.registries.DeferredRegister<T> inner;
    private final String modid;
    private final List<RegistryObject<T>> entries = new ArrayList<>();

    private DeferredRegister(ResourceKey<? extends Registry<T>> key, String modid) {
        this.inner = net.neoforged.neoforge.registries.DeferredRegister.create(key, modid);
        this.modid = modid;
    }

    public static <B> DeferredRegister<B> create(IForgeRegistry<B> registry, String modid) { return new DeferredRegister<>(registry.getRegistryKey(), modid); }
    public static <B> DeferredRegister<B> create(ResourceKey<? extends Registry<B>> key, String modid) { return new DeferredRegister<>(key, modid); }
    public static <B> DeferredRegister<B> create(ResourceLocation registryName, String modid) { return new DeferredRegister<>(ResourceKey.createRegistryKey(registryName), modid); }
    public static <B> DeferredRegister<B> createOptional(ResourceKey<? extends Registry<B>> key, String modid) { return create(key, modid); }
    public static <B> DeferredRegister<B> createOptional(ResourceLocation registryName, String modid) { return create(registryName, modid); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> sup) {
        DeferredHolder<T, I> h = inner.register(name, sup);
        RegistryObject<I> ro = new RegistryObject<>(h);
        entries.add((RegistryObject) ro);
        return ro;
    }

    public void register(IEventBus bus) { inner.register(bus); }
    public Collection<RegistryObject<T>> getEntries() { return Collections.unmodifiableList(entries); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ResourceKey<Registry<T>> getRegistryKey() { return (ResourceKey) inner.getRegistryKey(); }
    public ResourceLocation getRegistryName() { return inner.getRegistryName(); }
    public TagKey<T> createTagKey(String path) { return inner.createTagKey(path); }
    public TagKey<T> createTagKey(ResourceLocation location) { return inner.createTagKey(location); }
    public String getNamespace() { return modid; }
}
