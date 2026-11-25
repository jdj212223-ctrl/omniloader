package dev.omniloader.loader;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.locating.IModFile;

import java.io.IOException;

/** Adapter for Minecraft 26.1 / NeoForge 26.1 (FML 11). Everything version-specific in the loader lives here. */
public final class Platform {
    public static final String MC = "26.1";
    private Platform() {}

    public static boolean isClient() { return FMLLoader.getCurrent().getDist().isClient(); }

    public static byte[] readFile(IModFile file, String path) throws IOException {
        var c = file.getContents();
        return c.containsFile(path) ? c.readFile(path) : null;
    }
}
