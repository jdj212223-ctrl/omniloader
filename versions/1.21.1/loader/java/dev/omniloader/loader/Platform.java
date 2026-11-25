package dev.omniloader.loader;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.locating.IModFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Adapter for Minecraft 1.21.1 / NeoForge 21.1 (FML 4). Everything version-specific in the loader lives here. */
public final class Platform {
    public static final String MC = "1.21.1";
    /** Exact Minecraft version, for checking mods' version ranges. */
    public static final String MC_FULL = "1.21.1";
    /** NeoForge version this adapter was built and tested against. */
    public static final String NEO_VERSION = "21.1.264";
    /** Running on NeoForge (as opposed to Omniloader's own engine). */
    public static final boolean NEOFORGE = true;
    /** Newest class-file version the Java this Minecraft runs on can load. */
    public static final int MAX_CLASS_VERSION = 65;
    /** Minecraft shipped with scrambled names (Fabric mods use "intermediary" names for it). */
    public static final boolean OBFUSCATED = true;
    private Platform() {}

    public static java.nio.file.Path gameDir() { return net.neoforged.fml.loading.FMLPaths.GAMEDIR.get(); }

    public static boolean isClient() { return FMLLoader.getDist().isClient(); }

    public static byte[] readFile(IModFile file, String path) throws IOException {
        Path p = file.findResource(path);
        return Files.isRegularFile(p) ? Files.readAllBytes(p) : null;
    }
}
