package net.minecraftforge.registries;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Forge's handle to a registered thing (Omniloader stand-in over NeoForge's DeferredHolder). */
public final class RegistryObject<T> implements Supplier<T> {
    private final DeferredHolder<? super T, T> holder;

    RegistryObject(DeferredHolder<? super T, T> holder) { this.holder = holder; }

    public static <T, U extends T> RegistryObject<U> create(ResourceLocation name, IForgeRegistry<T> registry) {
        return new RegistryObject<>(DeferredHolder.create(registry.getRegistryKey(), name));
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T, U extends T> RegistryObject<U> create(ResourceLocation name, ResourceKey<? extends net.minecraft.core.Registry<T>> registryKey, String modid) {
        return new RegistryObject<>((DeferredHolder) DeferredHolder.create((ResourceKey) registryKey, name));
    }

    @Override public T get() { return holder.get(); }
    public ResourceLocation getId() { return holder.getId(); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ResourceKey<T> getKey() { return (ResourceKey) holder.getKey(); }
    public boolean isPresent() { return holder.isBound(); }
    public void ifPresent(Consumer<? super T> c) { if (isPresent()) c.accept(get()); }
    public Stream<T> stream() { return isPresent() ? Stream.of(get()) : Stream.empty(); }
    public <U> Optional<U> map(Function<? super T, ? extends U> f) { return isPresent() ? Optional.ofNullable(f.apply(get())) : Optional.empty(); }
    public T orElse(T other) { return isPresent() ? get() : other; }
    public T orElseGet(Supplier<? extends T> other) { return isPresent() ? get() : other.get(); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<Holder<T>> getHolder() { return Optional.of((Holder) holder); }
    @Override public boolean equals(Object o) { return o instanceof RegistryObject<?> r && r.getId().equals(getId()); }
    @Override public int hashCode() { return getId().hashCode(); }
    @Override public String toString() { return "RegistryObject{" + getId() + "}"; }
}
