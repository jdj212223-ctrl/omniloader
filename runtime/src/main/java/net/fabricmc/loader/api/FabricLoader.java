package net.fabricmc.loader.api;

import dev.omniloader.runtime.fabric.FabricLoaderImpl;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Omniloader's stand-in for the Fabric Loader API. */
public interface FabricLoader {
    static FabricLoader getInstance() { return FabricLoaderImpl.INSTANCE; }

    <T> List<T> getEntrypoints(String key, Class<T> type);
    <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type);
    <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker);
    ObjectShare getObjectShare();
    MappingResolver getMappingResolver();
    Optional<ModContainer> getModContainer(String id);
    Collection<ModContainer> getAllMods();
    boolean isModLoaded(String id);
    boolean isDevelopmentEnvironment();
    EnvType getEnvironmentType();
    @Deprecated Object getGameInstance();
    Path getGameDir();
    @Deprecated File getGameDirectory();
    Path getConfigDir();
    @Deprecated File getConfigDirectory();
    String[] getLaunchArguments(boolean sanitize);
}
