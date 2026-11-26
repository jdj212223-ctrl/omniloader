package dev.omniloader.runtime;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** In-game adapter for Minecraft 1.21.1 / NeoForge 21.1. */
public final class Platform {
    /** NeoForge 1.21.1 loads mods inside the game constructor: the game object already exists. */
    public static final boolean CLIENT_START_WITH_MODS = true;
    public static final String MC = "1.21.1";
    private Platform() {}

    public static boolean isClient() { return FMLEnvironment.dist.isClient(); }
    public static boolean isProduction() { return FMLLoader.isProduction(); }
    public static List<? extends IModFileInfo> modFiles() { return FMLLoader.getLoadingModList().getModFiles(); }
    public static List<? extends IModInfo> mods() { return FMLLoader.getLoadingModList().getMods(); }
    public static List<Path> roots(IModFile f) { return List.of(f.getSecureJar().getRootPath()); }

    public static String readText(IModFile f, String path) throws IOException {
        Path p = f.findResource(path);
        return Files.isRegularFile(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
    }

    /** Starts Omniloader's Fabric API stand-ins (where this version uses them). */
    public static void initFabricApi(net.neoforged.bus.api.IEventBus modBus) {
        // The real Fabric API (Forgified Fabric API, bundled) does this on 1.21.1.
    }

    /** Forge-style events for Forge mods (only called when Forge mods are running). */
    /** One mod menu for all mods (client only). */
    public static void initModMenu(net.neoforged.bus.api.IEventBus modBus) {
        dev.omniloader.runtime.ModMenuBridge.init(modBus);
    }

    public static void initForge() {
        dev.omniloader.runtime.forge.ForgeBridge.init();
    }
}
