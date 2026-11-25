package dev.omniloader.loader;

import dev.omniloader.loader.convert.ModMeta;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Runs first during mod discovery:
 *  1. adds Omniloader's runtime (the Fabric/Quilt API stand-ins) as a mod,
 *  2. takes a quick look at every jar in the mods folder so we know which mod ids exist,
 *     which Fabric/Quilt mods are missing something they need, and which jars are Forge mods.
 */
public class OmniLocator implements IModFileCandidateLocator {
    private static final Logger LOG = LoggerFactory.getLogger("Omniloader");
    private static final Pattern TOML_ID = Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"");

    @Override
    public int getPriority() { return 2000; }

    @Override
    public String toString() { return "omniloader"; }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        LOG.info("[Omniloader] Omniloader {} for Minecraft " + Platform.MC + " starting on the {} side - Fabric, Quilt and NeoForge mods welcome",
                version(), Platform.isClient() ? "client" : "server");
        addRuntime(pipeline);
        scanModsFolder(pipeline);
    }

    static String version() {
        String v = OmniLocator.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    private void addRuntime(IDiscoveryPipeline pipeline) {
        try (InputStream in = OmniLocator.class.getResourceAsStream("/META-INF/omniloader/omniloader-runtime.jar")) {
            if (in == null) {
                LOG.error("[Omniloader] runtime jar missing from Omniloader - Fabric mods will not start");
                return;
            }
            byte[] data = in.readAllBytes();
            Path target = OmniPaths.root().resolve("omniloader-runtime-" + OmniPaths.sha1(data).substring(0, 12) + ".jar");
            if (!Files.isRegularFile(target)) {
                Path tmp = Files.createTempFile(OmniPaths.root(), "runtime", ".tmp");
                Files.write(tmp, data);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            try (Stream<Path> old = Files.list(OmniPaths.root())) {
                old.filter(p -> p.getFileName().toString().startsWith("omniloader-runtime-") && !p.equals(target))
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
            pipeline.addPath(target, ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
        } catch (IOException e) {
            pipeline.addIssue(ModLoadingIssue.error("fml.modloadingissue.technical_error", "Omniloader could not unpack its runtime: " + e));
        }
    }

    private record Found(Path file, ModMeta meta, Set<String> apiRefs) {}

    private static final Pattern API_REF = Pattern.compile("net[/.]fabricmc[/.]fabric[/.](?:api|impl)[/.][A-Za-z0-9_/.$]+");

    /** Every Fabric API class a mod's code mentions (outer class names, slash form). */
    private static Set<String> apiRefs(ZipFile z) throws IOException {
        Set<String> out = new TreeSet<>();
        for (ZipEntry e : java.util.Collections.list(z.entries())) {
            if (!e.getName().endsWith(".class")) continue;
            String text = new String(z.getInputStream(e).readAllBytes(), StandardCharsets.ISO_8859_1);
            Matcher m = API_REF.matcher(text);
            while (m.find()) {
                String n = m.group().replace('.', '/');
                int d = n.indexOf('$');
                out.add(d > 0 ? n.substring(0, d) : n);
            }
        }
        return out;
    }

    /** The Fabric API classes Omniloader's runtime provides (read from the runtime jar we carry). */
    private static Set<String> providedApi() {
        Set<String> out = new java.util.HashSet<>();
        try (InputStream in = OmniLocator.class.getResourceAsStream("/META-INF/omniloader/omniloader-runtime.jar");
             ZipInputStream z = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String n = e.getName();
                if (n.startsWith("net/fabricmc/fabric/") && n.endsWith(".class") && !n.contains("$")) out.add(n.substring(0, n.length() - 6));
            }
        } catch (Exception ex) {
            LOG.warn("[Omniloader] Could not list the Fabric API parts we provide: {}", ex.toString());
        }
        return out;
    }

    private void scanModsFolder(IDiscoveryPipeline pipeline) {
        Path mods = FMLPaths.MODSDIR.get();
        List<Found> fabricMods = new ArrayList<>();
        int neo = 0, forge = 0;
        try (Stream<Path> s = Files.list(mods)) {
            for (Path p : s.filter(p -> p.toString().toLowerCase().endsWith(".jar")).sorted().toList()) {
                try (ZipFile z = new ZipFile(p.toFile())) {
                    ZipEntry neoToml = z.getEntry("META-INF/neoforge.mods.toml");
                    if (neoToml != null) {
                        neo++;
                        collectTomlIds(new String(z.getInputStream(neoToml).readAllBytes(), StandardCharsets.UTF_8));
                        continue;
                    }
                    ModMeta meta = metaOf(z);
                    if (meta != null) {
                        fabricMods.add(new Found(p, meta, apiRefs(z)));
                        register(meta);
                        for (String nested : meta.nestedJars) scanNested(z, nested, 0);
                        continue;
                    }
                    if (z.getEntry("META-INF/mods.toml") != null) {
                        forge++;
                        LOG.warn("[Omniloader] {} is a Forge mod. Forge mods are not supported yet (planned for a later version).", p.getFileName());
                    }
                } catch (IOException | RuntimeException e) {
                    LOG.warn("[Omniloader] Could not look inside {}: {}", p.getFileName(), e.toString());
                }
            }
        } catch (IOException e) {
            LOG.warn("[Omniloader] Could not list the mods folder: {}", e.toString());
        }

        Set<String> provided = fabricMods.isEmpty() ? Set.of() : providedApi();
        for (Found f : fabricMods) {
            List<String> missing = new ArrayList<>();
            boolean needsApi = false;
            for (String dep : f.meta.depends.keySet()) {
                if (OmniState.BUILT_IN.contains(dep) || OmniState.presentIds.contains(dep) || OmniState.presentIds.contains(ModMeta.neoId(dep))) continue;
                if (OmniState.isFabricApiId(dep)) { needsApi = true; continue; }
                missing.add(dep);
            }
            // Fabric API: instead of trusting the dependency list, check every Fabric API class the mod actually uses.
            List<String> missingApi = f.apiRefs.stream().filter(r -> !provided.contains(r))
                    .map(r -> r.substring(r.lastIndexOf('/') + 1)).distinct().toList();
            if (missing.isEmpty() && missingApi.isEmpty()) {
                if (needsApi) LOG.info("[Omniloader] {} uses Fabric API: all {} part(s) it uses are provided by Omniloader", f.meta.name, f.apiRefs.size());
                continue;
            }
            String reason;
            if (!missing.isEmpty()) reason = "it needs " + String.join(", ", missing) + ", which is not installed";
            else reason = "it uses parts of Fabric API that Omniloader doesn't have yet ("
                    + String.join(", ", missingApi.subList(0, Math.min(6, missingApi.size()))) + (missingApi.size() > 6 ? ", ..." : "") + ")";
            OmniState.disabled.put(f.meta.id, reason);
            LOG.warn("[Omniloader] Turning off {}: {}", f.meta.name, reason);
            pipeline.addIssue(ModLoadingIssue.warning(escape("Omniloader switched off " + f.meta.name + " because " + reason + "."))
                    .withAffectedPath(f.file));
        }
        LOG.info("[Omniloader] Mods folder: {} Fabric/Quilt, {} NeoForge, {} Forge (Forge not supported yet), {} switched off",
                fabricMods.size(), neo, forge, OmniState.disabled.size());
    }

    /** FML runs issue texts through MessageFormat, so quotes and braces must be escaped. */
    static String escape(String s) {
        return s.replace("'", "''").replace("{", "'{'").replace("}", "'}'");
    }

    private static void register(ModMeta meta) {
        OmniState.presentIds.add(meta.id);
        OmniState.presentIds.addAll(meta.provides);
    }

    private static void collectTomlIds(String toml) {
        Matcher m = TOML_ID.matcher(toml);
        while (m.find()) OmniState.presentIds.add(m.group(1));
    }

    private static ModMeta metaOf(ZipFile z) throws IOException {
        ZipEntry q = z.getEntry("quilt.mod.json");
        if (q != null) return ModMeta.quilt(new String(z.getInputStream(q).readAllBytes(), StandardCharsets.UTF_8));
        ZipEntry f = z.getEntry("fabric.mod.json");
        if (f != null) return ModMeta.fabric(new String(z.getInputStream(f).readAllBytes(), StandardCharsets.UTF_8));
        return null;
    }

    private static void scanNested(ZipFile outer, String path, int depth) throws IOException {
        ZipEntry e = outer.getEntry(path);
        if (e == null || depth > 4) return;
        scanNestedBytes(outer.getInputStream(e).readAllBytes(), depth);
    }

    private static void scanNestedBytes(byte[] jar, int depth) throws IOException {
        Map<String, byte[]> wanted = new LinkedHashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String n = e.getName();
                if (n.equals("fabric.mod.json") || n.equals("quilt.mod.json") || n.startsWith("META-INF/jars/") && n.endsWith(".jar"))
                    wanted.put(n, z.readAllBytes());
            }
        }
        ModMeta meta = wanted.containsKey("quilt.mod.json") ? ModMeta.quilt(new String(wanted.get("quilt.mod.json"), StandardCharsets.UTF_8))
                : wanted.containsKey("fabric.mod.json") ? ModMeta.fabric(new String(wanted.get("fabric.mod.json"), StandardCharsets.UTF_8)) : null;
        if (meta == null || OmniState.skipNested(meta.id)) return;
        register(meta);
        for (String n : meta.nestedJars) if (wanted.containsKey(n)) scanNestedBytes(wanted.get(n), depth + 1);
    }
}
