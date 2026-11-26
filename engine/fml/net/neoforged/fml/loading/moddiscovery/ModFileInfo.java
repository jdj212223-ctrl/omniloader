package net.neoforged.fml.loading.moddiscovery;
import net.neoforged.neoforgespi.language.*;
import org.apache.maven.artifact.versioning.VersionRange;
import java.util.*;
/** A parsed neoforge.mods.toml (the whole file). */
public class ModFileInfo implements IModFileInfo {
    private final ModFile file;
    private final NightConfigWrapper config;
    private final List<IModInfo> mods = new ArrayList<>();
    private final List<LanguageSpec> languages;
    private final Map<String, Object> properties;
    private final String license;
    private final boolean showAsResourcePack, showAsDataPack;
    private final List<String> usesServices;

    public ModFileInfo(ModFile file, NightConfigWrapper config) {
        this.file = file;
        this.config = config;
        String loader = config.<String>getConfigElement("modLoader").orElse("javafml");
        VersionRange range;
        try { range = VersionRange.createFromVersionSpec(config.<String>getConfigElement("loaderVersion").orElse("")); } catch (Exception e) { range = IModInfo.UNBOUNDED; }
        this.languages = List.of(new LanguageSpec(loader, range));
        this.properties = config.<Map<String, Object>>getConfigElement("properties").orElse(Map.of());
        this.license = config.<String>getConfigElement("license").orElse("");
        this.showAsResourcePack = config.<Boolean>getConfigElement("showAsResourcePack").orElse(false);
        this.showAsDataPack = config.<Boolean>getConfigElement("showAsDataPack").orElse(false);
        this.usesServices = config.<List<String>>getConfigElement("services").orElse(List.of());
        for (IConfigurable m : config.getConfigList("mods")) mods.add(new ModInfo(this, (NightConfigWrapper) m));
    }

    /** Fills in ${file.jarVersion} and ${global.*} placeholders. */
    String substitute(String s) {
        if (s == null || !s.contains("${")) return s;
        for (var e : file.getSubstitutionMap().get().entrySet()) s = s.replace("${" + e.getKey() + "}", String.valueOf(e.getValue()));
        return s;
    }

    public NightConfigWrapper config() { return config; }
    @Override public List<IModInfo> getMods() { return mods; }
    @Override public List<LanguageSpec> requiredLanguageLoaders() { return languages; }
    @Override public boolean showAsResourcePack() { return showAsResourcePack; }
    @Override public boolean showAsDataPack() { return showAsDataPack; }
    @Override public Map<String, Object> getFileProperties() { return properties; }
    @Override public String getLicense() { return license; }
    @Override public String moduleName() { return mods.isEmpty() ? file.getFileName() : mods.get(0).getModId(); }
    @Override public String versionString() { return mods.isEmpty() ? "" : mods.get(0).getVersion().toString(); }
    @Override public List<String> usesServices() { return usesServices; }
    @Override public ModFile getFile() { return file; }
    @Override public IConfigurable getConfig() { return config; }
    @Override public String toString() { return "mod file " + file.getFileName(); }
}
