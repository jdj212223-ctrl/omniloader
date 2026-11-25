package net.fabricmc.fabric.api.resource;

import dev.omniloader.runtime.fabricapi.ResourceBridge;
import net.minecraft.server.packs.PackType;

/** Fabric API: register resource reload listeners (game data on the server, textures/sounds/models on the client). */
public interface ResourceManagerHelper {
    void registerReloadListener(IdentifiableResourceReloadListener listener);

    static ResourceManagerHelper get(PackType type) { return listener -> ResourceBridge.add(type, listener); }
}
