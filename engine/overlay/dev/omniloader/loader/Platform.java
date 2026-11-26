package dev.omniloader.loader;

/** Adapter for Omniloader's own engine (Minecraft 1.21.1; NeoForge's API is added on top in full mode). */
public final class Platform {
    public static final String MC = "1.21.1";
    public static final String MC_FULL = "1.21.1";
    public static final String NEO_VERSION = "21.1.264";
    /** NeoForge's API (and its patched game classes) loaded on top? Set by the engine at start-up. */
    public static boolean NEOFORGE = false;
    public static final int MAX_CLASS_VERSION = 65;
    public static final boolean OBFUSCATED = true;
    private Platform() {}

    public static boolean isClient() { return dev.omniloader.engine.Engine.client; }
    public static java.nio.file.Path gameDir() { return dev.omniloader.engine.Engine.gameDir; }
}
