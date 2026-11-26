package net.neoforged.fml.loading;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.IBindingsProvider;
import java.nio.file.Path; import java.util.ServiceLoader;
/** Omniloader's answers to "what's the game set-up?" (set by Omniloader's engine at start-up). */
public class FMLLoader {
    public static Runnable progressWindowTick = () -> {};
    static Dist dist = Dist.CLIENT;
    static Path gamePath = Path.of(".");
    static LoadingModList loadingModList;
    static VersionInfo versionInfo = new VersionInfo("21.1.264", "4.0.45", "1.21.1", "20240808.144430");
    private static IBindingsProvider bindings;

    public static void setup(Dist d, Path game, VersionInfo v) { dist = d; gamePath = game; versionInfo = v; }
    public static void setLoadingModList(LoadingModList l) { loadingModList = l; }
    public static Dist getDist() { return dist; }
    public static LoadingModList getLoadingModList() { return loadingModList; }
    public static Path getGamePath() { return gamePath; }
    public static boolean isProduction() { return true; }
    public static VersionInfo versionInfo() { return versionInfo; }
    public static String launcherHandlerName() { return dist.isClient() ? "omniloader-client" : "omniloader-server"; }
    public static String getLauncherInfo() { return "Omniloader"; }
    public static synchronized IBindingsProvider getBindings() {
        if (bindings == null) bindings = ServiceLoader.load(IBindingsProvider.class, Thread.currentThread().getContextClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("NeoForge's game bindings are missing"));
        return bindings;
    }
}
