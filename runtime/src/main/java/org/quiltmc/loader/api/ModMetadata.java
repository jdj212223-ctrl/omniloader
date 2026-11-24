package org.quiltmc.loader.api;

public interface ModMetadata {
    String id();
    String group();
    Version version();
    String name();
    String description();
}
