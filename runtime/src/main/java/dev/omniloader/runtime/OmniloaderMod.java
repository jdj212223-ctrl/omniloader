package dev.omniloader.runtime;

import dev.omniloader.runtime.fabric.FabricLoaderImpl;
import dev.omniloader.runtime.fabric.ModContainerImpl;
import dev.omniloader.runtime.quilt.QuiltBridge;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Omniloader's in-game half. NeoForge starts this like any mod; it then starts the Fabric and Quilt mods
 * the same way Fabric Loader would: their "main" code first, then the client or server code.
 */
@Mod("omniloader")
public final class OmniloaderMod {
    private static final Logger LOG = LoggerFactory.getLogger("Omniloader");
    private static boolean started;

    public OmniloaderMod(IEventBus modBus) {
        FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;
        loader.setup();
        List<ModContainerImpl> foreign = loader.containers().stream().filter(c -> c.foreign).distinct().toList();
        StringBuilder sb = new StringBuilder();
        for (ModContainerImpl c : foreign) {
            sb.append("\n\t\t").append(c.meta().getType().equals("quilt") ? "[Quilt]  " : "[Fabric] ")
                    .append(c.getMetadata().getName()).append(' ').append(c.getMetadata().getVersion().getFriendlyString())
                    .append(" (").append(c.getMetadata().getId()).append(')').append(c.active ? "" : "  - switched off");
        }
        LOG.info("[Omniloader] {} Fabric/Quilt mod(s) running on NeoForge:{}", foreign.size(), sb);

        // Fabric API stand-ins: connect Fabric's events to NeoForge's.
        dev.omniloader.runtime.fabricapi.CommonBridge.init();
        if (FMLEnvironment.dist.isClient()) dev.omniloader.runtime.fabricapi.ClientBridge.init(modBus);

        run("preLaunch", "pre_launch");
        // Fabric mods register blocks/items straight into the game's registries while starting up.
        // NeoForge only allows that during its registry event, so that's where we start them.
        modBus.addListener(EventPriority.HIGHEST, RegisterEvent.class, e -> startMods());
    }

    private static synchronized void startMods() {
        if (started) return;
        started = true;
        long t0 = System.nanoTime();
        run("main", "init");
        if (FMLEnvironment.dist.isClient()) run("client", "client_init");
        else run("server", "server_init");
        LOG.info("[Omniloader] Fabric/Quilt mods started in {} ms", (System.nanoTime() - t0) / 1_000_000);
    }

    /** Runs one Fabric entrypoint kind and its Quilt twin. */
    private static void run(String fabricKey, String quiltKey) {
        FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;
        for (String key : new String[]{fabricKey, quiltKey}) {
            for (FabricLoaderImpl.Entry e : loader.rawEntrypoints(key)) {
                String who = e.mod().getMetadata().getName();
                try {
                    Object o = loader.instantiate(e, Object.class);
                    boolean ran = invoke(o, e.mod());
                    if (ran) LOG.info("[Omniloader] Started {} ({} -> {})", who, key, e.value());
                    else LOG.warn("[Omniloader] {}: entrypoint '{}' ({}) is not a type Omniloader knows how to start", who, key, e.value());
                } catch (Throwable t) {
                    throw new RuntimeException("Fabric/Quilt mod " + who + " crashed while starting (" + key + " -> " + e.value() + ")", t);
                }
            }
        }
    }

    private static boolean invoke(Object o, ModContainerImpl mod) {
        boolean ran = false;
        if (o instanceof net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint p) { p.onPreLaunch(); ran = true; }
        if (o instanceof org.quiltmc.loader.api.entrypoint.PreLaunchEntrypoint p) { p.onPreLaunch(QuiltBridge.wrap(mod)); ran = true; }
        if (o instanceof net.fabricmc.api.ModInitializer m) { m.onInitialize(); ran = true; }
        if (o instanceof org.quiltmc.qsl.base.api.entrypoint.ModInitializer m) { m.onInitialize(QuiltBridge.wrap(mod)); ran = true; }
        if (FMLEnvironment.dist.isClient()) {
            if (o instanceof net.fabricmc.api.ClientModInitializer m) { m.onInitializeClient(); ran = true; }
            if (o instanceof org.quiltmc.qsl.base.api.entrypoint.client.ClientModInitializer m) { m.onInitializeClient(QuiltBridge.wrap(mod)); ran = true; }
        } else {
            if (o instanceof net.fabricmc.api.DedicatedServerModInitializer m) { m.onInitializeServer(); ran = true; }
            if (o instanceof org.quiltmc.qsl.base.api.entrypoint.server.DedicatedServerModInitializer m) { m.onInitializeServer(QuiltBridge.wrap(mod)); ran = true; }
        }
        return ran;
    }
}
