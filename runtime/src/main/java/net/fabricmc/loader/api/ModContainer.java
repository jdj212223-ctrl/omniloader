package net.fabricmc.loader.api;

import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ModContainer {
    ModMetadata getMetadata();
    List<Path> getRootPaths();
    default Optional<Path> findPath(String file) {
        for (Path root : getRootPaths()) {
            Path p = root.resolve(file.replace("/", root.getFileSystem().getSeparator()));
            if (java.nio.file.Files.exists(p)) return Optional.of(p);
        }
        return Optional.empty();
    }
    ModOrigin getOrigin();
    Optional<ModContainer> getContainingMod();
    Collection<ModContainer> getContainedMods();
    @Deprecated default Path getRootPath() { return getRootPaths().get(0); }
    @Deprecated default Path getPath(String file) { return findPath(file).orElse(getRootPath().resolve(file)); }
}
