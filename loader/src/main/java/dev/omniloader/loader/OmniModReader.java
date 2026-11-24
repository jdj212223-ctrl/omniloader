package dev.omniloader.loader;

import cpw.mods.jarhandling.JarContents;
import dev.omniloader.loader.convert.JarConverter;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLLoader;
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
        if (!fabric && !quilt) return null;
        // Jars that ship for several loaders at once: let NeoForge load its own version.
        if (jar.findFile("META-INF/neoforge.mods.toml").isPresent()) return null;
        try {
            byte[] data = Files.readAllBytes(jar.getPrimaryPath());
            var metaPeek = JarConverter.readMeta(JarConverter.readZip(data));
            String disabled = metaPeek == null ? null : OmniState.disabled.get(metaPeek.id);
            String dist = FMLLoader.getDist().isClient() ? "client" : "server";
            JarConverter.Result r = JarConverter.convert(data, dist, disabled);
            if (r.active()) {
                LOG.info("[Omniloader] Loading {} mod {} {} ({})", r.meta().loader, r.meta().name, r.meta().version, jar.getPrimaryPath().getFileName());
            } else {
                LOG.info("[Omniloader] Listing {} mod {} but not starting it, because {}", r.meta().loader, r.meta().name, r.reason());
            }
            return JarModsDotTomlModFileReader.createModFile(JarContents.of(r.jar()), attributes.withReader(this));
        } catch (Exception e) {
            LOG.error("[Omniloader] Failed to convert {}", jar.getPrimaryPath(), e);
            throw new ModLoadingException(ModLoadingIssue.error(OmniLocator.escape(
                    "Omniloader could not convert " + (quilt ? "Quilt" : "Fabric") + " mod " + jar.getPrimaryPath().getFileName()
                            + ": " + e.getMessage())).withAffectedPath(jar.getPrimaryPath()).withCause(e));
        }
    }

    static String lower(String s) { return s.toLowerCase(Locale.ROOT); }
}
