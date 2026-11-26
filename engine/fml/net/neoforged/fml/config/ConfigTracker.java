package net.neoforged.fml.config;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import java.io.ByteArrayInputStream;
import java.nio.file.*; import java.util.*; import java.util.concurrent.*;
/** Omniloader's config manager: reads and writes mods' .toml config files (night-config, like NeoForge). */
public class ConfigTracker {
    public static final ConfigTracker INSTANCE = new ConfigTracker();
    final List<ModConfig> all = new CopyOnWriteArrayList<>();
    final EnumMap<ModConfig.Type, Set<ModConfig>> byType = new EnumMap<>(ModConfig.Type.class);
    final Map<String, ModConfig> byFile = new ConcurrentHashMap<>();
    public ConfigTracker() { for (ModConfig.Type t : ModConfig.Type.values()) byType.put(t, Collections.synchronizedSet(new LinkedHashSet<>())); }

    public ModConfig registerConfig(ModConfig.Type type, IConfigSpec spec, ModContainer container) {
        return registerConfig(type, spec, container, container.getModId() + "-" + type.extension() + ".toml");
    }

    public ModConfig registerConfig(ModConfig.Type type, IConfigSpec spec, ModContainer container, String fileName) {
        if (spec.isEmpty()) return null;
        ModConfig c = new ModConfig(type, spec, container, fileName);
        if (byFile.putIfAbsent(fileName, c) != null) throw new IllegalStateException("Config file " + fileName + " is registered twice");
        all.add(c);
        byType.get(type).add(c);
        spec.validateSpec(c);
        if (type == ModConfig.Type.STARTUP) open(c, FMLPaths.CONFIGDIR.get());
        return c;
    }

    public void loadConfigs(ModConfig.Type type, Path dir) { loadConfigs(type, dir, null); }

    public void loadConfigs(ModConfig.Type type, Path dir, Path defaultsDir) {
        for (ModConfig c : List.copyOf(byType.get(type))) {
            try {
                if (defaultsDir != null) {
                    Path target = dir.resolve(c.getFileName()), def = defaultsDir.resolve(c.getFileName());
                    if (!Files.exists(target) && Files.isRegularFile(def)) { Files.createDirectories(target.getParent()); Files.copy(def, target); }
                }
                open(c, dir);
            } catch (Exception e) {
                throw new RuntimeException("Could not load config " + c.getFileName() + " of " + c.getModId(), e);
            }
        }
    }

    private void open(ModConfig c, Path dir) {
        try {
            Path path = dir.resolve(c.getFileName());
            Files.createDirectories(path.getParent());
            CommentedFileConfig file = CommentedFileConfig.builder(path).sync().preserveInsertionOrder().build();
            try { file.load(); }
            catch (Exception broken) {
                // a broken file: keep a backup and start fresh, like NeoForge does
                Files.move(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                file = CommentedFileConfig.builder(path).sync().preserveInsertionOrder().build();
                file.load();
            }
            if (!c.getSpec().isCorrect(file)) { c.getSpec().correct(file); file.save(); }
            CommentedFileConfig f = file;
            c.fullPath = path;
            c.loaded = new IConfigSpec.ILoadedConfig() {
                public CommentedConfig config() { return f; }
                public void save() { f.save(); }
            };
            c.getSpec().acceptConfig(c.loaded);
            c.container().acceptEvent(new ModConfigEvent.Loading(c));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void unloadConfigs(ModConfig.Type type) {
        for (ModConfig c : List.copyOf(byType.get(type))) {
            if (c.loaded == null) continue;
            c.container().acceptEvent(new ModConfigEvent.Unloading(c));
            c.getSpec().acceptConfig(null);
            c.loaded = null;
        }
    }

    public static void acceptSyncedConfig(ModConfig c, byte[] bytes) {
        CommentedConfig parsed = TomlFormat.instance().createParser().parse(new ByteArrayInputStream(bytes));
        if (!c.getSpec().isCorrect(parsed)) c.getSpec().correct(parsed);
        c.loaded = new IConfigSpec.ILoadedConfig() {
            public CommentedConfig config() { return parsed; }
            public void save() {}
        };
        c.getSpec().acceptConfig(c.loaded);
        c.container().acceptEvent(new ModConfigEvent.Reloading(c));
    }

    public void loadDefaultServerConfigs() {
        for (ModConfig c : List.copyOf(byType.get(ModConfig.Type.SERVER))) {
            CommentedConfig cfg = CommentedConfig.inMemory();
            c.getSpec().correct(cfg);
            c.loaded = new IConfigSpec.ILoadedConfig() {
                public CommentedConfig config() { return cfg; }
                public void save() {}
            };
            c.getSpec().acceptConfig(c.loaded);
            c.container().acceptEvent(new ModConfigEvent.Loading(c));
        }
    }
}
