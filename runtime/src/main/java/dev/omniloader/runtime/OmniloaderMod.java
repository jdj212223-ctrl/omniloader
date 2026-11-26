package dev.omniloader.runtime;

import dev.omniloader.runtime.fabric.FabricLoaderImpl;
import dev.omniloader.runtime.fabric.ModContainerImpl;
import dev.omniloader.runtime.quilt.QuiltBridge;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
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
                    .append(" (").append(c.getMetadata().getId()).append(')').append(c.crossVersion ? "  [bridged from another Minecraft version]" : "").append(c.active ? "" : "  - switched off" + (c.offReason != null ? ": " + c.offReason : ""));
        }
        LOG.info("[Omniloader] {} Fabric/Quilt mod(s) on NeoForge for Minecraft {}:{}", foreign.size(), Platform.MC, sb);

        // Fabric API stand-ins: connect Fabric's events to NeoForge's.
        Platform.initFabricApi(modBus);
        if (Platform.isClient()) Platform.initModMenu(modBus);

        run("preLaunch", "pre_launch");
        // NeoForge mods adapted from another Minecraft version / newer Java: start the ones that fit.
        dev.omniloader.runtime.HeldBackStarter.startAll();
        ResourceHider.hideSwitchedOffMods();
        // Fabric mods register blocks/items straight into the game's registries while starting up.
        // NeoForge only allows that during its registry event, so that's where we start them.
        modBus.addListener(EventPriority.HIGHEST, RegisterEvent.class, e -> startMods());
    }

    private static boolean clientWanted, clientStarted;

    private static synchronized void startMods() {
        if (started) return;
        started = true;
        long t0 = System.nanoTime();
        run("main", "init");
        if (!Platform.isClient()) run("server", "server_init");
        else if (Platform.CLIENT_START_WITH_MODS || clientWanted) startClient();
        LOG.info("[Omniloader] Fabric/Quilt mods started in {} ms", (System.nanoTime() - t0) / 1_000_000);
    }

    /** Fabric/Quilt client code: once the game object exists (and after the mods' main code). */
    public static synchronized void startClient() {
        if (clientStarted) return;
        if (!started) { clientWanted = true; return; }   // main code first, like Fabric
        clientStarted = true;
        run("client", "client_init");
    }

    /** Runs one Fabric entrypoint kind and its Quilt twin. */
    private static void run(String fabricKey, String quiltKey) {
        FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;
        for (String key : new String[]{fabricKey, quiltKey}) {
            for (FabricLoaderImpl.Entry e : List.copyOf(loader.rawEntrypoints(key))) {
                if (!e.mod().active) continue;
                String who = e.mod().getMetadata().getName();
                try {
                    Object o = loader.instantiate(e, expectedType(key));
                    boolean ran = invoke(o, e.mod());
                    if (ran) LOG.info("[Omniloader] Started {} ({} -> {})", who, key, e.value());
                    else LOG.warn("[Omniloader] {}: entrypoint '{}' ({}) is not a type Omniloader knows how to start", who, key, e.value());
                } catch (Throwable t) {
                    // Only this mod is switched off; the game and the other mods carry on.
                    Throwable c = t instanceof java.lang.reflect.InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
                    LOG.error("[Omniloader] Fabric/Quilt mod {} crashed while starting ({} -> {}); switching it off", who, key, e.value(), c);
                    loader.switchOff(e.mod(), "it crashed while starting: " + c);
                    loader.noteSwitchedOff(e.mod());
                }
            }
        }
    }

    /** What a startup entry must be, for entries that point at a method ("Class::method"). */
    private static Class<?> expectedType(String key) {
        return switch (key) {
            case "main" -> net.fabricmc.api.ModInitializer.class;
            case "client" -> net.fabricmc.api.ClientModInitializer.class;
            case "server" -> net.fabricmc.api.DedicatedServerModInitializer.class;
            case "preLaunch" -> net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint.class;
            case "init" -> org.quiltmc.qsl.base.api.entrypoint.ModInitializer.class;
            case "client_init" -> org.quiltmc.qsl.base.api.entrypoint.client.ClientModInitializer.class;
            case "server_init" -> org.quiltmc.qsl.base.api.entrypoint.server.DedicatedServerModInitializer.class;
            case "pre_launch" -> org.quiltmc.loader.api.entrypoint.PreLaunchEntrypoint.class;
            default -> Object.class;
        };
    }

    private static boolean invoke(Object o, ModContainerImpl mod) {
        boolean ran = false;
        if (o instanceof net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint p) { p.onPreLaunch(); ran = true; }
        if (o instanceof org.quiltmc.loader.api.entrypoint.PreLaunchEntrypoint p) { p.onPreLaunch(QuiltBridge.wrap(mod)); ran = true; }
        if (o instanceof net.fabricmc.api.ModInitializer m) { m.onInitialize(); ran = true; }
        if (o instanceof org.quiltmc.qsl.base.api.entrypoint.ModInitializer m) { m.onInitialize(QuiltBridge.wrap(mod)); ran = true; }
        if (Platform.isClient()) {
            if (o instanceof net.fabricmc.api.ClientModInitializer m) { m.onInitializeClient(); ran = true; }
            if (o instanceof org.quiltmc.qsl.base.api.entrypoint.client.ClientModInitializer m) { m.onInitializeClient(QuiltBridge.wrap(mod)); ran = true; }
        } else {
            if (o instanceof net.fabricmc.api.DedicatedServerModInitializer m) { m.onInitializeServer(); ran = true; }
            if (o instanceof org.quiltmc.qsl.base.api.entrypoint.server.DedicatedServerModInitializer m) { m.onInitializeServer(QuiltBridge.wrap(mod)); ran = true; }
        }
        return ran;
    }
}
