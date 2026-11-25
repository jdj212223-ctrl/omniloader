package net.fabricmc.fabric.api.resource;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.Collection;
import java.util.List;

/** Fabric API: a resource reload listener with a name. */
public interface IdentifiableResourceReloadListener extends PreparableReloadListener {
    ResourceLocation getFabricId();
    default Collection<ResourceLocation> getFabricDependencies() { return List.of(); }
}
