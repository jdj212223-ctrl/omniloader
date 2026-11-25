package net.minecraftforge.fml;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

/** Forge's "does the server need this mod too?" marker (Omniloader stand-in; NeoForge doesn't use it). */
public final class IExtensionPoint$DisplayTest implements net.neoforged.fml.IExtensionPoint {
    public static final String IGNORESERVERONLY = "OHNOES😱😱😱😱😱😱😱😱😱😱😱😱😱😱😱😱😱";
    /** "Works with any version on the other side, or none." */
    public static final Supplier<IExtensionPoint$DisplayTest> IGNORE_ALL_VERSION = () -> new IExtensionPoint$DisplayTest(IGNORESERVERONLY, (a, b) -> true);
    /** "Client-side mod: the server doesn't need it." */
    public static final Supplier<IExtensionPoint$DisplayTest> IGNORE_SERVER_VERSION = IGNORE_ALL_VERSION;
    private final Supplier<String> suppliedVersion;
    private final BiPredicate<String, Boolean> remoteVersionTest;

    public IExtensionPoint$DisplayTest(Supplier<String> suppliedVersion, BiPredicate<String, Boolean> remoteVersionTest) {
        this.suppliedVersion = suppliedVersion; this.remoteVersionTest = remoteVersionTest;
    }
    public IExtensionPoint$DisplayTest(String version, BiPredicate<String, Boolean> remoteVersionTest) { this(() -> version, remoteVersionTest); }
    public Supplier<String> suppliedVersion() { return suppliedVersion; }
    public BiPredicate<String, Boolean> remoteVersionTest() { return remoteVersionTest; }
}
