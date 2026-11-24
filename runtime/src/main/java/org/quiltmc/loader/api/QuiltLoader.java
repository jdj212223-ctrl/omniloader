package org.quiltmc.loader.api;

import dev.omniloader.runtime.quilt.QuiltBridge;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Quilt Loader API (Omniloader stand-in, backed by the same mod list as the Fabric one). */
public final class QuiltLoader {
    private QuiltLoader() {}
    public static boolean isModLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
    public static Optional<ModContainer> getModContainer(String id) { return FabricLoader.getInstance().getModContainer(id).map(QuiltBridge::wrap); }
    public static Collection<ModContainer> getAllMods() { return FabricLoader.getInstance().getAllMods().stream().map(QuiltBridge::wrap).toList(); }
    public static <T> List<T> getEntrypoints(String key, Class<T> type) { return FabricLoader.getInstance().getEntrypoints(key, type); }
    public static Path getGameDir() { return FabricLoader.getInstance().getGameDir(); }
    public static Path getConfigDir() { return FabricLoader.getInstance().getConfigDir(); }
    public static boolean isDevelopmentEnvironment() { return FabricLoader.getInstance().isDevelopmentEnvironment(); }
    public static net.fabricmc.loader.api.MappingResolver getMappingResolver() { return FabricLoader.getInstance().getMappingResolver(); }
    public static net.fabricmc.loader.api.ObjectShare getObjectShare() { return FabricLoader.getInstance().getObjectShare(); }
    public static String[] getLaunchArguments(boolean sanitize) { return new String[0]; }
}
