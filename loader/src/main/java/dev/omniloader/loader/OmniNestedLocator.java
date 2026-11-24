package dev.omniloader.loader;

import dev.omniloader.loader.convert.JarConverter;
import dev.omniloader.loader.convert.ModMeta;
import net.neoforged.neoforgespi.locating.IDependencyLocator;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Fabric and Quilt mods often carry other jars inside them (libraries or smaller mods).
 * This unpacks them and feeds them back into NeoForge's discovery, where they get converted too.
 */
public class OmniNestedLocator implements IDependencyLocator {
    private static final Logger LOG = LoggerFactory.getLogger("Omniloader");

    @Override
    public String toString() { return "omniloader nested jars"; }

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        Set<String> seen = new HashSet<>();
        for (IModFile mod : loadedMods) {
            Path marker = mod.findResource("META-INF/omniloader/mod.json");
            if (!Files.isRegularFile(marker)) continue;
            try {
                byte[] data = Files.readAllBytes(mod.getFilePath());
                unpack(JarConverter.readZip(data), mod, pipeline, seen, 0);
            } catch (Exception e) {
                LOG.warn("[Omniloader] Could not read jars nested in {}: {}", mod.getFileName(), e.toString());
            }
        }
    }

    private void unpack(Map<String, byte[]> files, IModFile parent, IDiscoveryPipeline pipeline, Set<String> seen, int depth) throws IOException {
        ModMeta meta = JarConverter.readMeta(files);
        if (meta == null || depth > 4) return;
        for (String path : meta.nestedJars) {
            byte[] jar = files.get(path);
            if (jar == null) continue;
            Map<String, byte[]> inner = JarConverter.readZip(jar);
            ModMeta innerMeta = JarConverter.readMeta(inner);
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (innerMeta != null && OmniState.skipNested(innerMeta.id)) {
                LOG.info("[Omniloader] Skipping bundled {} inside {} (provided elsewhere)", innerMeta.id, meta.id);
                continue;
            }
            if (inner.containsKey("META-INF/neoforge.mods.toml")) continue;
            String key = innerMeta != null ? "mod:" + innerMeta.id : "lib:" + name.replaceAll("-[0-9].*$", "");
            if (!seen.add(key)) continue;

            Path out = OmniPaths.nested().resolve(OmniPaths.sha1(jar).substring(0, 16) + "-" + name);
            if (!Files.isRegularFile(out)) {
                byte[] bytes = innerMeta != null ? jar : asLibrary(inner, name);
                Path tmp = Files.createTempFile(OmniPaths.nested(), "nested", ".tmp");
                Files.write(tmp, bytes);
                Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
            }
            LOG.info("[Omniloader] Found {} {} inside {}", innerMeta != null ? "bundled mod" : "bundled library", name, meta.id);
            pipeline.addPath(out, ModFileDiscoveryAttributes.DEFAULT.withParent(parent), IncompatibleFileReporting.WARN_ALWAYS);
            if (innerMeta != null) unpack(inner, parent, pipeline, seen, depth + 1);
        }
    }

    /** Plain Java libraries need a small label so NeoForge loads them as a library. */
    private static byte[] asLibrary(Map<String, byte[]> files, String name) throws IOException {
        Manifest mf = new Manifest();
        if (files.containsKey("META-INF/MANIFEST.MF")) mf.read(new ByteArrayInputStream(files.get("META-INF/MANIFEST.MF")));
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        mf.getMainAttributes().putValue("FMLModType", "GAMELIBRARY");
        mf.getEntries().clear();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (JarOutputStream jo = new JarOutputStream(bo, mf)) {
            for (var e : files.entrySet()) {
                String n = e.getKey();
                String u = n.toUpperCase();
                if (n.equals("META-INF/MANIFEST.MF") || u.startsWith("META-INF/") && (u.endsWith(".SF") || u.endsWith(".RSA") || u.endsWith(".DSA"))) continue;
                jo.putNextEntry(new ZipEntry(n));
                jo.write(e.getValue());
                jo.closeEntry();
            }
        }
        return bo.toByteArray();
    }
}
