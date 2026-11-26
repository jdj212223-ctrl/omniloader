package net.neoforged.fml.config;
import java.util.*;
public final class ModConfigs {
    public static List<ModConfig> getModConfigs(String modId) { return ConfigTracker.INSTANCE.all.stream().filter(c -> c.getModId().equals(modId)).toList(); }
    public static List<String> getConfigFileNames(String modId, ModConfig.Type type) {
        return ConfigTracker.INSTANCE.all.stream().filter(c -> c.getModId().equals(modId) && c.getType() == type && c.getFullPath() != null).map(c -> c.getFullPath().toString()).toList();
    }
    public static Set<ModConfig> getConfigSet(ModConfig.Type type) { return Collections.unmodifiableSet(ConfigTracker.INSTANCE.byType.get(type)); }
    public static Map<String, ModConfig> getFileMap() { return Collections.unmodifiableMap(ConfigTracker.INSTANCE.byFile); }
}
