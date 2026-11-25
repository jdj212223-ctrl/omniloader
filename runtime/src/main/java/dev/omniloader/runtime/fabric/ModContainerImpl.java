package dev.omniloader.runtime.fabric;

import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class ModContainerImpl implements ModContainer {
    private final ModMetadataImpl meta;
    private final List<Path> roots;
    private final Path origin;
    /** Fabric/Quilt mod running through Omniloader (as opposed to a NeoForge or built-in mod). */
    public final boolean foreign;
    public boolean active;
    /** Made for a different Minecraft version (bridged by Omniloader). */
    public boolean crossVersion;
    public String offReason;
    public String builtFor;
    public int builtForJava;
    public final String neoId;
    public ModContainerImpl parent;
    public final List<ModContainer> children = new ArrayList<>();

    public ModContainerImpl(ModMetadataImpl meta, List<Path> roots, Path origin, boolean foreign, boolean active, String neoId) {
        this.meta = meta; this.roots = roots; this.origin = origin; this.foreign = foreign; this.active = active; this.neoId = neoId;
    }

    public ModMetadataImpl meta() { return meta; }
    public ModMetadata getMetadata() { return meta; }
    public List<Path> getRootPaths() { return roots; }
    public Optional<ModContainer> getContainingMod() { return Optional.ofNullable(parent); }
    public Collection<ModContainer> getContainedMods() { return children; }
    public ModOrigin getOrigin() {
        return new ModOrigin() {
            public Kind getKind() { return origin == null ? Kind.UNKNOWN : Kind.PATH; }
            public List<Path> getPaths() { return origin == null ? List.of() : List.of(origin); }
            public String getParentModId() { return parent == null ? null : parent.getMetadata().getId(); }
            public String getParentSubLocation() { return null; }
        };
    }
    @Override public String toString() { return meta.getId() + " " + meta.getVersion().getFriendlyString(); }
}
