package org.quiltmc.loader.api;

import java.nio.file.Path;

public interface ModContainer {
    ModMetadata metadata();
    Path rootPath();
    default Path getPath(String path) { return rootPath().resolve(path); }
}
