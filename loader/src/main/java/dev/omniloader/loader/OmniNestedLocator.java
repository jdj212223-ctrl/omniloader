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
            try {
                if (Platform.readFile(mod, "META-INF/omniloader/mod.json") == null) continue;
                byte[] data = Files.readAllBytes(mod.getFilePath());
                NestedJars.extract(data, p -> pipeline.addPath(p, ModFileDiscoveryAttributes.DEFAULT.withParent(mod), IncompatibleFileReporting.WARN_ALWAYS), seen);
            } catch (Exception e) {
                LOG.warn("[Omniloader] Could not read jars nested in {}: {}", mod.getFileName(), e.toString());
            }
        }
    }

}
