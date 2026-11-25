package dev.omniloader.loader;

import net.neoforged.fml.jarcontents.JarContents;
import dev.omniloader.loader.convert.JarConverter;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.Locale;

/**
 * Asked about every jar NeoForge finds. If it's a Fabric or Quilt mod (and not also a NeoForge mod),
 * we hand NeoForge a translated copy instead.
 */
public class OmniModReader implements IModFileReader {
    private static final Logger LOG = LoggerFactory.getLogger("Omniloader");

    @Override
    public int getPriority() { return 2000; }

    @Override
    public String toString() { return "omniloader"; }

    @Override
    public @Nullable IModFile read(JarContents jar, ModFileDiscoveryAttributes attributes) {
        boolean fabric = jar.findFile("fabric.mod.json").isPresent();
        boolean quilt = jar.findFile("quilt.mod.json").isPresent();
        boolean neo = jar.findFile("META-INF/neoforge.mods.toml").isPresent();
        try {
            ReaderCore.Outcome r = ReaderCore.read(jar.getPrimaryPath(), fabric, quilt, neo);
            if (r == null) return null;
            LOG.info("[Omniloader] {}", r.message());
            return JarModsDotTomlModFileReader.createModFile(JarContents.ofPath(r.jar()), attributes.withReader(this));
        } catch (Exception e) {
            LOG.error("[Omniloader] Failed to convert {}", jar.getPrimaryPath(), e);
            throw new ModLoadingException(ModLoadingIssue.error(OmniLocator.escape(
                    "Omniloader could not convert " + jar.getPrimaryPath().getFileName() + ": " + e.getMessage()))
                    .withAffectedPath(jar.getPrimaryPath()).withCause(e));
        }
    }

    static String lower(String s) { return s.toLowerCase(Locale.ROOT); }
}
