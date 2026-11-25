package dev.omniloader.loader;

import dev.omniloader.loader.convert.ClassVersions;
import dev.omniloader.loader.convert.JarConverter;
import dev.omniloader.loader.convert.NeoJarConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Decides what Omniloader does with each jar NeoForge finds (shared by every Minecraft version's adapter).
 * Returns the jar NeoForge should load instead, or null to leave the jar alone.
 */
public final class ReaderCore {
    private static final Logger LOG = LoggerFactory.getLogger("Omniloader");

    private ReaderCore() {}

    public record Outcome(Path jar, String message) {}

    public static Outcome read(Path primary, boolean fabric, boolean quilt, boolean neoToml) throws Exception {
        String dist = Platform.isClient() ? "client" : "server";
        if ((fabric || quilt) && !neoToml) {
            byte[] data = Files.readAllBytes(primary);
            var metaPeek = JarConverter.readMeta(JarConverter.readZip(data));
            String disabled = metaPeek == null ? null : OmniState.disabled.get(metaPeek.id);
            JarConverter.Result r = JarConverter.convert(data, dist, disabled);
            String msg = r.active()
                    ? "Loading " + r.meta().loader + " mod " + r.meta().name + " " + r.meta().version + " (" + primary.getFileName() + ")"
                    : "Listing " + r.meta().loader + " mod " + r.meta().name + " but not starting it, because " + r.reason();
            return new Outcome(r.jar(), msg);
        }
        // NeoForge mods and libraries: only touched if made for another Minecraft version or a newer Java.
        if (!Files.isRegularFile(primary)) return null; // folders (development setups) are left alone
        Peek p = peek(primary);
        if (p == null) return null;
        // Forge mods (mods.toml, not NeoForge's neoforge.mods.toml)
        if (p.toml == null && p.forgeToml && dev.omniloader.loader.convert.ForgeConverter.supported()) {
            Path out = dev.omniloader.loader.convert.ForgeConverter.convert(Files.readAllBytes(primary), p.newestJava);
            return new Outcome(out, "Translating Forge mod " + primary.getFileName() + " to NeoForge");
        }
        NeoJarConverter.Check check = NeoJarConverter.check(p.toml, p.newestJava);
        if (!check.needed()) return null;
        Path out = NeoJarConverter.convert(Files.readAllBytes(primary), check);
        return new Outcome(out, "Adapting " + primary.getFileName() + " (made for "
                + (check.mcRange() != null ? "Minecraft " + check.mcRange() : "another version")
                + (check.newestJava() > ClassVersions.java(Platform.MAX_CLASS_VERSION) ? ", Java " + check.newestJava() : "") + ")");
    }

    private record Peek(String toml, int newestJava, boolean forgeToml) {}

    /** Reads the mod description and the Java version of a few class files, without unpacking the whole jar. */
    private static Peek peek(Path jar) throws IOException {
        if (jar.getFileSystem() == FileSystems.getDefault()) {
            try (ZipFile z = new ZipFile(jar.toFile())) {
                ZipEntry t = z.getEntry("META-INF/neoforge.mods.toml");
                boolean forge = z.getEntry("META-INF/mods.toml") != null;
                String toml = t == null ? null : new String(z.getInputStream(t).readAllBytes(), StandardCharsets.UTF_8);
                int newest = 0, seen = 0;
                for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements() && seen < 8; ) {
                    ZipEntry e = en.nextElement();
                    if (!e.getName().endsWith(".class") || e.getName().startsWith("META-INF/")) continue;
                    byte[] head = z.getInputStream(e).readNBytes(8);
                    newest = Math.max(newest, ClassVersions.java(ClassVersions.major(head)));
                    seen++;
                }
                return new Peek(toml, newest, forge);
            }
        }
        // Jar inside another jar: read it in full.
        String toml = null;
        boolean forge = false;
        int newest = 0, seen = 0;
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(Files.readAllBytes(jar)))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.getName().equals("META-INF/neoforge.mods.toml")) toml = new String(z.readAllBytes(), StandardCharsets.UTF_8);
                else if (e.getName().equals("META-INF/mods.toml")) forge = true;
                else if (seen < 8 && e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/")) {
                    newest = Math.max(newest, ClassVersions.java(ClassVersions.major(z.readNBytes(8))));
                    seen++;
                }
            }
        }
        return new Peek(toml, newest, forge);
    }
}
