package dev.omniloader.runtime;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** In-game adapter for Minecraft 26.1 / NeoForge 26.1. */
public final class Platform {
    /** NeoForge 26.1 loads mods before the game object exists: start client code from the game constructor (MinecraftStartMixin). */
    public static final boolean CLIENT_START_WITH_MODS = false;
    public static final String MC = "26.1";
    private Platform() {}

    public static boolean isClient() { return FMLLoader.getCurrent().getDist().isClient(); }
    public static boolean isProduction() { return FMLLoader.getCurrent().isProduction(); }
    public static List<? extends IModFileInfo> modFiles() { return FMLLoader.getCurrent().getLoadingModList().getModFiles(); }
    public static List<? extends IModInfo> mods() { return FMLLoader.getCurrent().getLoadingModList().getMods(); }
    public static List<Path> roots(IModFile f) { return new ArrayList<>(f.getContents().getContentRoots()); }

    public static String readText(IModFile f, String path) throws IOException {
        var c = f.getContents();
        return c.containsFile(path) ? new String(c.readFile(path), StandardCharsets.UTF_8) : null;
    }

    /** Starts Omniloader's Fabric API stand-ins (where this version uses them). */
    public static void initFabricApi(net.neoforged.bus.api.IEventBus modBus) {
        // The real Fabric API (Forgified Fabric API, bundled) does this on 26.1.2 too.
    }

    /** Forge-style events for Forge mods (only called when Forge mods are running). */
    /** One mod menu for all mods (client only). */
    public static void initModMenu(net.neoforged.bus.api.IEventBus modBus) {
        dev.omniloader.runtime.ModMenuBridge.init(modBus);
    }

    public static void initForge() {
        // Forge mods are not supported on this edition.
    }
}
