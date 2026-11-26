package dev.omniloader.loader;

import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/** NeoForge edition: runs first during NeoForge's mod discovery and hands it what {@link ModsFolderScan} finds. */
public class OmniLocator implements IModFileCandidateLocator {
    private static final Logger LOG = LoggerFactory.getLogger("Omniloader");

    @Override
    public int getPriority() { return 2000; }

    @Override
    public String toString() { return "omniloader"; }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        // Installed both as a loader and as a jar in mods/? Only the first copy takes charge.
        if (System.getProperty("omniloader.active") != null) {
            LOG.warn("[Omniloader] A second copy of Omniloader was found (installed as a loader AND in the mods folder); ignoring it");
            return;
        }
        System.setProperty("omniloader.active", version());
        LOG.info("[Omniloader] Omniloader {} for Minecraft " + Platform.MC + " starting on the {} side - Fabric, Quilt and NeoForge mods welcome",
                version(), Platform.isClient() ? "client" : "server");
        ModsFolderScan.run(FMLPaths.MODSDIR.get(), new ModsFolderScan.Sink() {
            @Override public void add(Path jar, boolean required) {
                pipeline.addPath(jar, ModFileDiscoveryAttributes.DEFAULT, required ? IncompatibleFileReporting.ERROR : IncompatibleFileReporting.WARN_ALWAYS);
            }
            @Override public void warn(String message, Path file) { pipeline.addIssue(ModLoadingIssue.warning(escape(message)).withAffectedPath(file)); }
            @Override public void error(String message) { pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.technical_error", message)); }
        });
    }

    static String version() { return ModsFolderScan.version(); }

    /** FML runs issue texts through MessageFormat, so quotes and braces must be escaped. */
    static String escape(String s) {
        return s.replace("'", "''").replace("{", "'{'").replace("}", "'}'");
    }
}
