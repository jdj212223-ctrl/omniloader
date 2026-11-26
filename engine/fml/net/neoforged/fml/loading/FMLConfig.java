package net.neoforged.fml.loading;
import java.util.*;
/** Loader settings. Omniloader keeps NeoForge's defaults (no update checks, no separate loading window). */
public class FMLConfig {
    public enum ConfigValue {
        DISABLE_CONFIG_WATCHER(false), EARLY_WINDOW_CONTROL(false), MAX_THREADS(-1), VERSION_CHECK(false), DEFAULT_CONFIG_PATH("defaultconfigs"),
        DISABLE_OPTIMIZED_DFU(true), EARLY_WINDOW_PROVIDER("none"), EARLY_WINDOW_WIDTH(854), EARLY_WINDOW_HEIGHT(480), EARLY_WINDOW_FBSCALE(1),
        EARLY_WINDOW_MAXIMIZED(false), EARLY_WINDOW_SKIP_GL_VERSIONS(List.of()), EARLY_WINDOW_SQUIR(false);
        final Object value;
        ConfigValue(Object v) { value = v; }
    }
    public static void load() {}
    public static String getConfigValue(ConfigValue v) { return String.valueOf(v.value); }
    public static boolean getBoolConfigValue(ConfigValue v) { return v.value instanceof Boolean b && b; }
    public static int getIntConfigValue(ConfigValue v) { return v.value instanceof Integer i ? i : 0; }
    @SuppressWarnings("unchecked") public static <A> List<A> getListConfigValue(ConfigValue v) { return v.value instanceof List<?> l ? (List<A>) l : List.of(); }
    public static <T> void updateConfig(ConfigValue v, T value) {}
    public static String defaultConfigPath() { return "defaultconfigs"; }
}
