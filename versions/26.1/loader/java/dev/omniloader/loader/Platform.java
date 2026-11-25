package dev.omniloader.loader;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.locating.IModFile;

import java.io.IOException;

/** Adapter for Minecraft 26.1 / NeoForge 26.1 (FML 11). Everything version-specific in the loader lives here. */
public final class Platform {
    public static final String MC = "26.1";
    /** Exact Minecraft version, for checking mods' version ranges. */
    public static final String MC_FULL = "26.1.2";
    /** NeoForge version this adapter was built and tested against. */
    public static final String NEO_VERSION = "26.1.2.126";
    /** Newest class-file version the Java this Minecraft runs on can load. */
    public static final int MAX_CLASS_VERSION = 69;
    /** Minecraft shipped with scrambled names (Fabric mods use "intermediary" names for it). */
    public static final boolean OBFUSCATED = false;
    private Platform() {}

    public static boolean isClient() { return FMLLoader.getCurrent().getDist().isClient(); }

    public static byte[] readFile(IModFile file, String path) throws IOException {
        var c = file.getContents();
        return c.containsFile(path) ? c.readFile(path) : null;
    }
}
