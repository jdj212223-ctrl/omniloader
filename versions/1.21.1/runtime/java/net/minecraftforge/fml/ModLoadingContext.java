package net.minecraftforge.fml;

import net.neoforged.fml.IExtensionPoint;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;

import java.util.function.Supplier;

/** Forge's ModLoadingContext (Omniloader stand-in over NeoForge's). */
public final class ModLoadingContext {
    private static final ModLoadingContext INSTANCE = new ModLoadingContext();
    private ModLoadingContext() {}

    public static ModLoadingContext get() { return INSTANCE; }
    public ModContainer getActiveContainer() { return net.neoforged.fml.ModLoadingContext.get().getActiveContainer(); }
    public ModContainer getContainer() { return getActiveContainer(); }
    public String getActiveNamespace() { return net.neoforged.fml.ModLoadingContext.get().getActiveNamespace(); }

    public void registerConfig(ModConfig.Type type, IConfigSpec spec) { getActiveContainer().registerConfig(type, spec); }
    public void registerConfig(ModConfig.Type type, IConfigSpec spec, String fileName) { getActiveContainer().registerConfig(type, spec, fileName); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends IExtensionPoint> void registerExtensionPoint(Class<? extends T> point, Supplier<T> extension) {
        dev.omniloader.runtime.forge.ForgeExtensionPoints.register(getActiveContainer(), (Class) point, (Supplier) extension);
    }
}
