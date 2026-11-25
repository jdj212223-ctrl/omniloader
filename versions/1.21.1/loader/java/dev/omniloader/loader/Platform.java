package dev.omniloader.loader;

import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.locating.IModFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Adapter for Minecraft 1.21.1 / NeoForge 21.1 (FML 4). Everything version-specific in the loader lives here. */
public final class Platform {
    public static final String MC = "1.21.1";
    private Platform() {}

    public static boolean isClient() { return FMLLoader.getDist().isClient(); }

    public static byte[] readFile(IModFile file, String path) throws IOException {
        Path p = file.findResource(path);
        return Files.isRegularFile(p) ? Files.readAllBytes(p) : null;
    }
}
