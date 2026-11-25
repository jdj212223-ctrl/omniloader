package dev.omniloader.loader;

/** Adapter for Omniloader's own engine (plain Minecraft 1.21.1, no NeoForge). */
public final class Platform {
    public static final String MC = "1.21.1";
    public static final String MC_FULL = "1.21.1";
    public static final String NEO_VERSION = "0";
    /** Running on NeoForge? Not here: this is Omniloader's own engine. */
    public static final boolean NEOFORGE = false;
    public static final int MAX_CLASS_VERSION = 65;
    public static final boolean OBFUSCATED = true;
    private Platform() {}

    public static boolean isClient() { return dev.omniloader.engine.Engine.client; }
    public static java.nio.file.Path gameDir() { return dev.omniloader.engine.Engine.gameDir; }
}
