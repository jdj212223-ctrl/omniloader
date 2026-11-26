package net.neoforged.fml.loading.moddiscovery;
import net.neoforged.neoforgespi.language.*;
import net.neoforged.neoforgespi.locating.ForgeFeature;
import org.apache.maven.artifact.versioning.*;
import java.net.URL; import java.util.*;
/** One [[mods]] entry of a neoforge.mods.toml. */
public class ModInfo implements IModInfo {
    private final ModFileInfo owner;
    private final NightConfigWrapper config;
    private final String modId, displayName, description, namespace;
    private final ArtifactVersion version;
    private final List<ModVersion> dependencies = new ArrayList<>();
    private final Map<String, Object> properties;
    private final Optional<String> logo;
    private final boolean logoBlur;
    private final Optional<URL> updateUrl, modUrl;

    public ModInfo(ModFileInfo owner, NightConfigWrapper config) {
        this.owner = owner;
        this.config = config;
        this.modId = config.<String>getConfigElement("modId").orElseThrow(() -> new IllegalArgumentException("mod without modId in " + owner.getFile().getFileName()));
        this.namespace = config.<String>getConfigElement("namespace").orElse(modId);
        String v = owner.substitute(config.<Object>getConfigElement("version").map(String::valueOf).orElse("1"));
        this.version = new DefaultArtifactVersion(v);
        this.displayName = config.<String>getConfigElement("displayName").orElse(modId);
        this.description = config.<String>getConfigElement("description").orElse("");
        this.logo = config.<String>getConfigElement("logoFile").or(() -> owner.getConfig().getConfigElement("logoFile"));
        this.logoBlur = config.<Boolean>getConfigElement("logoBlur").or(() -> owner.getConfig().getConfigElement("logoBlur")).orElse(true);
        this.updateUrl = config.<String>getConfigElement("updateJSONURL").map(ModInfo::url);
        this.modUrl = config.<String>getConfigElement("modUrl").or(() -> config.getConfigElement("displayURL")).map(ModInfo::url);
        this.properties = owner.getConfig().<Map<String, Object>>getConfigElement("modproperties", modId).orElse(Map.of());
        for (IConfigurable d : owner.getConfig().getConfigList("dependencies", modId)) dependencies.add(new Dependency(this, (NightConfigWrapper) d, owner));
    }

    static URL url(String s) { try { return new URL(s); } catch (Exception e) { return null; } }

    @Override public ModFileInfo getOwningFile() { return owner; }
    @Override public IModLanguageLoader getLoader() { return null; }
    @Override public String getModId() { return modId; }
    @Override public String getDisplayName() { return displayName; }
    @Override public String getDescription() { return description; }
    @Override public ArtifactVersion getVersion() { return version; }
    @Override public List<? extends ModVersion> getDependencies() { return dependencies; }
    @Override public List<? extends ForgeFeature.Bound> getForgeFeatures() { return List.of(); }
    @Override public String getNamespace() { return namespace; }
    @Override public Map<String, Object> getModProperties() { return properties; }
    @Override public Optional<URL> getUpdateURL() { return updateUrl; }
    @Override public Optional<URL> getModURL() { return modUrl; }
    @Override public Optional<String> getLogoFile() { return logo; }
    @Override public boolean getLogoBlur() { return logoBlur; }
    @Override public IConfigurable getConfig() { return config; }
    @Override public String toString() { return modId + "@" + version; }

    static final class Dependency implements ModVersion {
        private IModInfo owner;
        private final String modId;
        private final VersionRange range;
        private final DependencyType type;
        private final Ordering ordering;
        private final DependencySide side;
        private final Optional<String> reason;
        private final Optional<URL> referral;
        Dependency(IModInfo owner, NightConfigWrapper c, ModFileInfo file) {
            this.owner = owner;
            this.modId = c.<String>getConfigElement("modId").orElse("?");
            String type = c.<String>getConfigElement("type").orElse(null);
            if (type == null) type = c.<Boolean>getConfigElement("mandatory").map(m -> m ? "required" : "optional").orElse("required");
            this.type = DependencyType.valueOf(type.toUpperCase(Locale.ROOT));
            VersionRange r;
            try { r = VersionRange.createFromVersionSpec(file.substitute(c.<String>getConfigElement("versionRange").orElse(""))); } catch (Exception e) { r = UNBOUNDED; }
            this.range = r;
            this.ordering = Ordering.valueOf(c.<String>getConfigElement("ordering").orElse("NONE").toUpperCase(Locale.ROOT));
            this.side = DependencySide.valueOf(c.<String>getConfigElement("side").orElse("BOTH").toUpperCase(Locale.ROOT));
            this.reason = c.getConfigElement("reason");
            this.referral = c.<String>getConfigElement("referralUrl").map(ModInfo::url);
        }
        @Override public String getModId() { return modId; }
        @Override public VersionRange getVersionRange() { return range; }
        @Override public DependencyType getType() { return type; }
        @Override public Optional<String> getReason() { return reason; }
        @Override public Ordering getOrdering() { return ordering; }
        @Override public DependencySide getSide() { return side; }
        @Override public void setOwner(IModInfo o) { owner = o; }
        @Override public IModInfo getOwner() { return owner; }
        @Override public Optional<URL> getReferralURL() { return referral; }
    }
}
