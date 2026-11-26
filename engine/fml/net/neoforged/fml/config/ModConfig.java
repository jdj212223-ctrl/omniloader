package net.neoforged.fml.config;
import net.neoforged.fml.ModContainer;
import java.nio.file.Path; import java.util.Locale;
public final class ModConfig {
    private final Type type; private final IConfigSpec spec; private final ModContainer container; private final String fileName;
    IConfigSpec.ILoadedConfig loaded; Path fullPath;
    ModConfig(Type type, IConfigSpec spec, ModContainer container, String fileName) { this.type = type; this.spec = spec; this.container = container; this.fileName = fileName; }
    public Type getType() { return type; }
    public String getFileName() { return fileName; }
    public IConfigSpec getSpec() { return spec; }
    public String getModId() { return container.getModId(); }
    public IConfigSpec.ILoadedConfig getLoadedConfig() { return loaded; }
    public Path getFullPath() { return fullPath; }
    ModContainer container() { return container; }
    public enum Type {
        COMMON, CLIENT, SERVER, STARTUP;
        public String extension() { return name().toLowerCase(Locale.ROOT); }
    }
}
