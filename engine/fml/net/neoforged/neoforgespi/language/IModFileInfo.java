package net.neoforged.neoforgespi.language;
import org.apache.maven.artifact.versioning.VersionRange;
import java.util.*;
public interface IModFileInfo {
    List<IModInfo> getMods();
    record LanguageSpec(String languageName, VersionRange acceptedVersions) {}
    List<LanguageSpec> requiredLanguageLoaders();
    boolean showAsResourcePack();
    boolean showAsDataPack();
    Map<String, Object> getFileProperties();
    String getLicense();
    String moduleName();
    String versionString();
    List<String> usesServices();
    net.neoforged.neoforgespi.locating.IModFile getFile();
    IConfigurable getConfig();
}
