package net.neoforged.fml.i18n;
import org.apache.maven.artifact.versioning.*;
public class MavenVersionTranslator {
    public static String artifactVersionToString(ArtifactVersion v) { return v.toString(); }
    public static String versionRangeToString(VersionRange r) { return r.toString(); }
    public static String restrictionToString(Restriction r) { return r.toString(); }
}
