package net.neoforged.fml.loading;
public record VersionInfo(String neoForgeVersion, String fmlVersion, String mcVersion, String neoFormVersion) {
    public String mcAndFmlVersion() { return mcVersion + "-" + fmlVersion; }
    public String mcAndNeoFormVersion() { return mcVersion + "-" + neoFormVersion; }
}
