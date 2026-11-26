package net.neoforged.fml.loading;
import java.nio.file.*;
public enum FMLPaths {
    GAMEDIR(""), MODSDIR("mods"), CONFIGDIR("config"), FMLCONFIG("config/fml.toml");
    private final String rel;
    FMLPaths(String rel) { this.rel = rel; }
    public Path relative() { return Path.of(rel); }
    public Path get() {
        Path p = rel.isEmpty() ? FMLLoader.getGamePath() : FMLLoader.getGamePath().resolve(rel);
        if (this == MODSDIR || this == CONFIGDIR) try { Files.createDirectories(p); } catch (java.io.IOException ignored) {}
        return p;
    }
    public static Path getOrCreateGameRelativePath(Path p) {
        Path out = FMLLoader.getGamePath().resolve(p);
        try { Files.createDirectories(out); } catch (java.io.IOException ignored) {}
        return out;
    }
}
