package net.neoforged.neoforgespi.language;
import org.apache.maven.artifact.versioning.*;
import java.net.URL; import java.util.*;
public interface IModInfo {
    VersionRange UNBOUNDED = MavenVersionAdapter.createFromVersionSpec("");
    IModFileInfo getOwningFile();
    IModLanguageLoader getLoader();
    String getModId();
    String getDisplayName();
    String getDescription();
    ArtifactVersion getVersion();
    List<? extends ModVersion> getDependencies();
    List<? extends net.neoforged.neoforgespi.locating.ForgeFeature.Bound> getForgeFeatures();
    String getNamespace();
    Map<String, Object> getModProperties();
    Optional<URL> getUpdateURL();
    Optional<URL> getModURL();
    Optional<String> getLogoFile();
    boolean getLogoBlur();
    IConfigurable getConfig();

    enum Ordering { BEFORE, AFTER, NONE }
    enum DependencySide {
        CLIENT, SERVER, BOTH;
        public boolean isContained(net.neoforged.api.distmarker.Dist side) { return this == BOTH || side.name().equals(name()) || side.name().equals("DEDICATED_SERVER") && this == SERVER; }
        public boolean isCorrectSide() { return isContained(net.neoforged.fml.loading.FMLEnvironment.dist); }
    }
    enum DependencyType { REQUIRED, OPTIONAL, INCOMPATIBLE, DISCOURAGED }
    interface ModVersion {
        String getModId();
        VersionRange getVersionRange();
        DependencyType getType();
        Optional<String> getReason();
        Ordering getOrdering();
        DependencySide getSide();
        void setOwner(IModInfo owner);
        IModInfo getOwner();
        Optional<URL> getReferralURL();
    }
    final class MavenVersionAdapter {
        private MavenVersionAdapter() {}
        public static VersionRange createFromVersionSpec(String spec) {
            try { return VersionRange.createFromVersionSpec(spec); } catch (InvalidVersionSpecificationException e) { throw new RuntimeException(e); }
        }
    }
}
