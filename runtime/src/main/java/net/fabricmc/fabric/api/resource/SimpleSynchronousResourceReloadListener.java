package net.fabricmc.fabric.api.resource;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Fabric API: a reload listener that does its work in one go (onResourceManagerReload). */
public interface SimpleSynchronousResourceReloadListener extends IdentifiableResourceReloadListener, ResourceManagerReloadListener {
}
