package dev.omniloader.engine;

import dev.omniloader.runtime.fabric.FabricLoaderImpl;
import dev.omniloader.runtime.fabric.ModContainerImpl;
import dev.omniloader.runtime.quilt.QuiltBridge;

/** Runs mods' start-up code, the way Fabric and Quilt do. */
final class EntrypointRunner {
    private EntrypointRunner() {}

    static void run(String fabricKey, String quiltKey) {
        for (String key : new String[]{fabricKey, quiltKey}) {
            for (var e : FabricLoaderImpl.INSTANCE.rawEntrypoints(key)) {
                String who = e.mod().getMetadata().getName();
                try {
                    Object o = FabricLoaderImpl.INSTANCE.instantiate(e, type(key));
                    if (invoke(o, e.mod())) Engine.log("Started " + who + " (" + key + ")");
                } catch (Throwable t) {
                    throw new RuntimeException("Mod " + who + " crashed while starting (" + key + " -> " + e.value() + ")", t);
                }
            }
        }
    }

    private static Class<?> type(String key) {
        return switch (key) {
            case "main" -> net.fabricmc.api.ModInitializer.class;
            case "client" -> net.fabricmc.api.ClientModInitializer.class;
            case "server" -> net.fabricmc.api.DedicatedServerModInitializer.class;
            case "init" -> org.quiltmc.qsl.base.api.entrypoint.ModInitializer.class;
            case "client_init" -> org.quiltmc.qsl.base.api.entrypoint.client.ClientModInitializer.class;
            case "server_init" -> org.quiltmc.qsl.base.api.entrypoint.server.DedicatedServerModInitializer.class;
            default -> Object.class;
        };
    }

    private static boolean invoke(Object o, ModContainerImpl mod) {
        boolean ran = false;
        if (o instanceof net.fabricmc.api.ModInitializer m) { m.onInitialize(); ran = true; }
        if (o instanceof org.quiltmc.qsl.base.api.entrypoint.ModInitializer m) { m.onInitialize(QuiltBridge.wrap(mod)); ran = true; }
        if (Engine.client) {
            if (o instanceof net.fabricmc.api.ClientModInitializer m) { m.onInitializeClient(); ran = true; }
            if (o instanceof org.quiltmc.qsl.base.api.entrypoint.client.ClientModInitializer m) { m.onInitializeClient(QuiltBridge.wrap(mod)); ran = true; }
        } else {
            if (o instanceof net.fabricmc.api.DedicatedServerModInitializer m) { m.onInitializeServer(); ran = true; }
            if (o instanceof org.quiltmc.qsl.base.api.entrypoint.server.DedicatedServerModInitializer m) { m.onInitializeServer(QuiltBridge.wrap(mod)); ran = true; }
        }
        return ran;
    }
}
