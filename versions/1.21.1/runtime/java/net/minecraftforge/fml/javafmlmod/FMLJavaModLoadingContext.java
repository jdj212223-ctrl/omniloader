package net.minecraftforge.fml.javafmlmod;

import net.minecraftforge.fml.ModLoadingContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.IExtensionPoint;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;

import java.util.function.Supplier;

/** Forge's per-mod loading context (Omniloader stand-in: it's the mod's NeoForge container underneath). */
public final class FMLJavaModLoadingContext {
    private final ModContainer container;

    public FMLJavaModLoadingContext(ModContainer container) { this.container = container; }

    public static FMLJavaModLoadingContext get() { return new FMLJavaModLoadingContext(ModLoadingContext.get().getActiveContainer()); }

    public IEventBus getModEventBus() { return container.getEventBus(); }
    public ModContainer getContainer() { return container; }
    public void registerConfig(ModConfig.Type type, IConfigSpec spec) { container.registerConfig(type, spec); }
    public void registerConfig(ModConfig.Type type, IConfigSpec spec, String fileName) { container.registerConfig(type, spec, fileName); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends IExtensionPoint> void registerExtensionPoint(Class<? extends T> point, Supplier<T> extension) {
        dev.omniloader.runtime.forge.ForgeExtensionPoints.register(container, (Class) point, (Supplier) extension);
    }
    public void registerDisplayTest(Object displayTest) {}
}
