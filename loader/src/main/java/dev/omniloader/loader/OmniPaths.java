package dev.omniloader.loader;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Where Omniloader keeps its working files: <game folder>/.omniloader */
public final class OmniPaths {
    /** Bump when the converter output changes, so old converted jars are rebuilt. */
    public static final String CONVERTER_VERSION = "28";

    private OmniPaths() {}

    public static Path root() {
        return dir(FMLPaths.GAMEDIR.get().resolve(".omniloader"));
    }

    public static Path cache() { return dir(root().resolve("converted")); }
    public static Path nested() { return dir(root().resolve("nested")); }
    public static Path mappings() { return dir(root().resolve("mappings")); }

    private static Path dir(Path p) {
        try {
            Files.createDirectories(p);
        } catch (IOException e) {
            throw new RuntimeException("Omniloader could not create folder " + p, e);
        }
        return p;
    }

    public static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String sha1(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
