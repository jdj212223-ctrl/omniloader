package dev.omniloader.engine;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import dev.omniloader.loader.ModsFolderScan;
import dev.omniloader.loader.NestedJars;
import dev.omniloader.loader.OmniPaths;
import dev.omniloader.loader.ReaderCore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.javafmlmod.FMLModContainer;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.VersionInfo;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import net.neoforged.fml.loading.moddiscovery.NightConfigWrapper;
import net.neoforged.neoforgespi.coremod.ICoreMod;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Full mode of Omniloader's engine: Fabric, Quilt, Forge and NeoForge mods together.
 * Omniloader loads everything itself and provides the loader API that NeoForge's API expects (see engine/fml);
 * NeoForge's API and its changed game classes ride on top as libraries. No FML, no ModLauncher.
 */
final class NeoBoot {
    static final String NEO = dev.omniloader.loader.Platform.NEO_VERSION, NEOFORM = "20240808.144430", MC = "1.21.1";

    record GameFiles(Path patched, Path game, Path extra, Path universal) {}

    private NeoBoot() {}

    /** Finds NeoForge's files in the launcher's library folder (or as given with -Domniloader.neo.*). */
    static GameFiles locate(boolean client) {
        String side = client ? "client" : "server";
        Path lib = System.getProperty("libraryDirectory") != null ? Path.of(System.getProperty("libraryDirectory")) : Engine.gameDir.resolve("libraries");
        Path patched = prop("omniloader.neo.patched", lib.resolve("net/neoforged/neoforge/" + NEO + "/neoforge-" + NEO + "-" + side + ".jar"));
        Path universal = prop("omniloader.neo.universal", lib.resolve("net/neoforged/neoforge/" + NEO + "/neoforge-" + NEO + "-universal.jar"));
        Path game = prop("omniloader.neo.game", lib.resolve("net/minecraft/" + side + "/" + MC + "-" + NEOFORM + "/" + side + "-" + MC + "-" + NEOFORM + "-srg.jar"));
        Path extra = prop("omniloader.neo.extra", lib.resolve("net/minecraft/" + side + "/" + MC + "-" + NEOFORM + "/" + side + "-" + MC + "-" + NEOFORM + "-extra.jar"));
        for (Path p : List.of(patched, universal, game, extra)) if (!Files.isRegularFile(p)) return null;
        return new GameFiles(patched, game, extra, universal);
    }

    private static Path prop(String key, Path def) { String v = System.getProperty(key); return v != null ? Path.of(v) : def; }

    // ------------------------------------------------------------------------------------------------ start-up

    private static final List<ModFile> modFiles = new ArrayList<>();
    private static final List<ModLoadingIssue> issues = new ArrayList<>();
    private static final Map<String, Path> jarJar = new LinkedHashMap<>();       // group:artifact -> chosen jar
    private static final Map<String, String> jarJarVersion = new HashMap<>();
    private static final Set<Path> libraries = new LinkedHashSet<>();

    static void launch(GameFiles files, String mainClass) throws Throwable {
        long t0 = System.currentTimeMillis();
        dev.omniloader.loader.Platform.NEOFORGE = true;
        Dist dist = Engine.client ? Dist.CLIENT : Dist.DEDICATED_SERVER;
        FMLLoader.setup(dist, Engine.gameDir, new VersionInfo(NEO, "omniloader-" + Engine.VERSION, MC, NEOFORM));
        Engine.log("Full mode: Fabric, Quilt, Forge and NeoForge mods (NeoForge API " + NEO + " on Omniloader's own loader)");

        EngineClassLoader loader = new EngineClassLoader(Engine.class.getClassLoader());
        Engine.loader = loader;
        Engine.access = new AccessRules();
        loader.addJar(files.patched());   // NeoForge's changed game classes win over the plain ones
        loader.addJar(files.game());
        loader.addJar(files.extra());
        Thread.currentThread().setContextClassLoader(loader);

        // 1. gather jars: NeoForge itself, Omniloader's runtime + bundled Fabric API, the mods folder
        List<Path> candidates = new ArrayList<>();
        candidates.add(files.universal());
        Path modsDir = Engine.gameDir.resolve("mods");
        Files.createDirectories(modsDir);
        Files.createDirectories(Engine.gameDir.resolve("config"));
        ModsFolderScan.run(modsDir, new ModsFolderScan.Sink() {
            public void add(Path jar, boolean required) { candidates.add(jar); }
            public void warn(String message, Path file) { issues.add(ModLoadingIssue.warning(escape(message)).withAffectedPath(file)); Engine.log(message); }
            public void error(String message) { issues.add(ModLoadingIssue.error("fml.modloadingissue.technical_error", message)); }
        });
        try (Stream<Path> s = Files.list(modsDir)) {
            s.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".jar")).sorted().forEach(candidates::add);
        }
        Set<String> seenNested = new HashSet<>();
        for (Path p : candidates) consider(p, true, seenNested);
        // jars inside jars (NeoForge "jar-in-jar"): newest version of each wins
        Set<Path> done = new HashSet<>();
        while (true) {
            List<Path> next = jarJar.values().stream().filter(done::add).toList();
            if (next.isEmpty()) break;
            for (Path p : next) consider(p, false, seenNested);
        }

        // 2. one copy per mod id (newest), drop mods whose required mods are missing, sort by dependencies
        List<ModFile> files2 = dedupe(modFiles);
        files2 = dropMissingDependencies(files2);
        List<ModInfo> sorted = sort(files2);
        ModFile minecraft = minecraftModFile(files.extra());
        files2.add(0, minecraft);
        sorted.add(0, (ModInfo) minecraft.getModInfos().get(0));

        // 3. classes, access changes, annotations, mixin configs
        List<String> mixinConfigs = new ArrayList<>();
        for (ModFile f : files2) {
            if (f == minecraft) continue;
            loader.addJar(f.getFilePath());
            readAccessTransformers(f);
            mixinConfigs.addAll(mixinConfigs(f));
            f.setScanResult(AnnotationScanner.scan(f.root()));
        }
        for (Path lib : libraries) loader.addJar(lib);
        List<ModFileInfo> infos = new ArrayList<>();
        for (ModFile f : files2) infos.add(f.getModFileInfo());
        LoadingModList.create(infos, sorted, issues);
        net.neoforged.fml.ModList.of(files2, sorted);
        ModLoader.setContainerFactory(info -> info.getModId().equals("minecraft") ? new MinecraftModContainer(info) : new FMLModContainer(info, info.getOwningFile().getFile().getScanResult(), loader));
        Engine.log(sorted.size() + " mods (" + files2.size() + " files, " + libraries.size() + " libraries) in " + (System.currentTimeMillis() - t0) + " ms");

        // 4. coremods (NeoForge's own small game-class changes) and Mixin
        Map<String, List<ITransformer<?>>> coremods = coremods(loader);
        Engine.startMixin(mixinConfigs);
        initMixinExtras(loader);
        loader.setGenerator(name -> { var t = dev.omniloader.engine.mixin.OmniMixinService.transformer(); return t == null ? null : t.generateClass(org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment(), name); });
        loader.setTransformer((name, bytes) -> {
            String internal = name.replace('.', '/');
            byte[] b = Engine.access.touches(internal) ? Engine.access.transform(internal, bytes) : bytes;
            List<ITransformer<?>> cm = coremods.get(name);
            if (cm != null) b = runCoremods(name, b, cm);
            var t = dev.omniloader.engine.mixin.OmniMixinService.transformer();
            return t == null ? b : t.transformClassBytes(name, name, b);
        });

        // 5. Minecraft (NeoForge's start-up code inside it calls Omniloader's ModLoader from here on)
        Engine.log("Starting Minecraft");
        loader.loadClass(mainClass).getMethod("main", String[].class).invoke(null, (Object) Engine.args);
    }

    static String escape(String s) { return s.replace("'", "''").replace("{", "'{'").replace("}", "'}'"); }

    /** Looks at one jar: converts it if needed (Fabric, Quilt, Forge, other versions), then records it as a mod or library. */
    private static void consider(Path jar, boolean topLevel, Set<String> seenNested) {
        try {
            boolean fabric, quilt, neo, forge;
            try (ZipFile z = new ZipFile(jar.toFile())) {
                fabric = z.getEntry("fabric.mod.json") != null;
                quilt = z.getEntry("quilt.mod.json") != null;
                neo = z.getEntry("META-INF/neoforge.mods.toml") != null;
                forge = z.getEntry("META-INF/mods.toml") != null;
            }
            Path use = jar;
            ReaderCore.Outcome r = ReaderCore.read(jar, fabric, quilt, neo);
            if (r != null) {
                Engine.log(r.message());
                use = r.jar();
                if ((fabric || quilt) && !neo) NestedJars.extract(Files.readAllBytes(jar), p -> consider(p, false, seenNested), seenNested);
            }
            try (ZipFile z = new ZipFile(use.toFile())) {
                ZipEntry toml = z.getEntry("META-INF/neoforge.mods.toml");
                readJarJar(z, use);
                if (toml != null) {
                    addModFile(use, z, new String(z.getInputStream(toml).readAllBytes(), StandardCharsets.UTF_8));
                    return;
                }
                String type = manifest(z).getMainAttributes().getValue("FMLModType");
                if (!topLevel || type != null && (type.equals("LIBRARY") || type.equals("GAMELIBRARY"))) libraries.add(use);
                else if (!fabric && !quilt && !forge) Engine.log("Skipping " + jar.getFileName() + " (not a mod)");
            }
        } catch (Exception e) {
            Engine.log("Could not load " + jar.getFileName() + ": " + e);
            issues.add(ModLoadingIssue.warning(escape("Omniloader could not load " + jar.getFileName() + ": " + e.getMessage())).withAffectedPath(jar));
        }
    }

    private static Manifest manifest(ZipFile z) throws IOException {
        ZipEntry e = z.getEntry("META-INF/MANIFEST.MF");
        Manifest m = new Manifest();
        if (e != null) try (InputStream in = z.getInputStream(e)) { m.read(in); }
        return m;
    }

    private static void readJarJar(ZipFile z, Path owner) throws IOException {
        ZipEntry meta = z.getEntry("META-INF/jarjar/metadata.json");
        if (meta == null) return;
        JsonObject root = JsonParser.parseString(new String(z.getInputStream(meta).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        for (var el : root.getAsJsonArray("jars")) {
            JsonObject j = el.getAsJsonObject();
            JsonObject id = j.getAsJsonObject("identifier");
            String key = id.get("group").getAsString() + ":" + id.get("artifact").getAsString();
            if (key.startsWith("io.github.llamalad7:mixinextras-") && !key.endsWith("neoforge")) continue; // one MixinExtras is enough
            String version = j.getAsJsonObject("version").get("artifactVersion").getAsString();
            String old = jarJarVersion.get(key);
            if (old != null && new DefaultArtifactVersion(old).compareTo(new DefaultArtifactVersion(version)) >= 0) continue;
            ZipEntry inner = z.getEntry(j.get("path").getAsString());
            if (inner == null) continue;
            byte[] data = z.getInputStream(inner).readAllBytes();
            Path dir = OmniPaths.root().resolve("engine").resolve("jarjar");
            Files.createDirectories(dir);
            String name = j.get("path").getAsString();
            Path out = dir.resolve(OmniPaths.sha1(data).substring(0, 12) + "-" + name.substring(name.lastIndexOf('/') + 1));
            if (!Files.isRegularFile(out)) { Path tmp = Files.createTempFile(dir, "jij", ".tmp"); Files.write(tmp, data); Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING); }
            jarJarVersion.put(key, version);
            jarJar.put(key, out);
        }
    }

    private static void addModFile(Path jar, ZipFile z, String tomlText) throws IOException {
        CommentedConfig cfg = new TomlParser().parse(tomlText);
        Map<String, Object> subs = new HashMap<>();
        String implVersion = manifest(z).getMainAttributes().getValue("Implementation-Version");
        subs.put("file.jarVersion", implVersion != null ? implVersion : "0.0NONE");
        subs.put("global.mcVersion", MC);
        subs.put("global.neoForgeVersion", NEO);
        subs.put("global.forgeVersion", NEO);
        subs.put("global.fmlVersion", "4.0.45");
        Path root = FileSystems.newFileSystem(jar).getPath("/");
        ModFile mf = new ModFile(jar, root, IModFile.Type.MOD, ModFileDiscoveryAttributes.DEFAULT, subs);
        mf.setInfo(new ModFileInfo(mf, new NightConfigWrapper(cfg)));
        modFiles.add(mf);
    }

    private static ModFile minecraftModFile(Path extra) throws IOException {
        String toml = "modLoader=\"minecraft\"\nloaderVersion=\"1\"\nlicense=\"Mojang Studios, All Rights Reserved\"\n"
                + "[[mods]]\nmodId=\"minecraft\"\nversion=\"" + MC + "\"\ndisplayName=\"Minecraft\"\ndescription=\"Minecraft\"\n";
        Path root = FileSystems.newFileSystem(extra).getPath("/");
        ModFile mf = new ModFile(extra, root, IModFile.Type.GAMELIBRARY, ModFileDiscoveryAttributes.DEFAULT, Map.of());
        mf.setInfo(new ModFileInfo(mf, new NightConfigWrapper(new TomlParser().parse(toml))));
        return mf;
    }

    private static List<ModFile> dedupe(List<ModFile> files) {
        Map<String, ModFile> owner = new HashMap<>();
        Set<ModFile> drop = new HashSet<>();
        for (ModFile f : files) for (IModInfo m : f.getModInfos()) {
            ModFile prev = owner.get(m.getModId());
            if (prev == null) { owner.put(m.getModId(), f); continue; }
            IModInfo pm = prev.getModInfos().stream().filter(x -> x.getModId().equals(m.getModId())).findFirst().orElseThrow();
            boolean newer = m.getVersion().compareTo(pm.getVersion()) > 0;
            ModFile loser = newer ? prev : f;
            if (newer) owner.put(m.getModId(), f);
            drop.add(loser);
            Engine.log("Two copies of " + m.getModId() + ": using " + (newer ? f : prev).getFileName() + ", ignoring " + loser.getFileName());
        }
        List<ModFile> out = new ArrayList<>();
        for (ModFile f : files) if (!drop.contains(f)) out.add(f);
        return out;
    }

    private static final Set<String> BUILT_IN = Set.of("minecraft", "neoforge", "java", "forge", "fml", "javafml");

    private static List<ModFile> dropMissingDependencies(List<ModFile> files) {
        List<ModFile> out = new ArrayList<>(files);
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<String> present = new HashSet<>(BUILT_IN);
            for (ModFile f : out) for (IModInfo m : f.getModInfos()) present.add(m.getModId());
            for (Iterator<ModFile> it = out.iterator(); it.hasNext(); ) {
                ModFile f = it.next();
                for (IModInfo m : f.getModInfos()) {
                    List<String> missing = m.getDependencies().stream()
                            .filter(d -> d.getType() == IModInfo.DependencyType.REQUIRED && d.getSide().isContained(FMLLoader.getDist()) && !present.contains(d.getModId()))
                            .map(IModInfo.ModVersion::getModId).toList();
                    if (missing.isEmpty()) continue;
                    String msg = "Not loading " + m.getDisplayName() + ": it needs " + String.join(", ", missing) + ", which is not installed";
                    Engine.log(msg);
                    issues.add(ModLoadingIssue.warning(escape(msg)).withAffectedPath(f.getFilePath()));
                    it.remove();
                    changed = true;
                    break;
                }
            }
        }
        return out;
    }

    /** Mods load after the mods they depend on (and before/after where they ask). NeoForge first. */
    private static List<ModInfo> sort(List<ModFile> files) {
        Map<String, ModInfo> byId = new LinkedHashMap<>();
        for (ModFile f : files) for (IModInfo m : f.getModInfos()) byId.put(m.getModId(), (ModInfo) m);
        Map<String, Set<String>> after = new HashMap<>();   // mod -> mods that must come before it
        for (ModInfo m : byId.values()) {
            Set<String> before = after.computeIfAbsent(m.getModId(), k -> new HashSet<>());
            if (!m.getModId().equals("neoforge")) before.add("neoforge");
            for (var d : m.getDependencies()) {
                if (!byId.containsKey(d.getModId())) continue;
                if (d.getOrdering() == IModInfo.Ordering.BEFORE) after.computeIfAbsent(d.getModId(), k -> new HashSet<>()).add(m.getModId());
                else if (d.getOrdering() == IModInfo.Ordering.AFTER || d.getType() == IModInfo.DependencyType.REQUIRED) before.add(d.getModId());
            }
        }
        List<ModInfo> out = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        TreeSet<String> ready = new TreeSet<>();
        while (out.size() < byId.size()) {
            ready.clear();
            for (String id : byId.keySet()) if (!placed.contains(id) && placed.containsAll(after.getOrDefault(id, Set.of()).stream().filter(byId::containsKey).toList())) ready.add(id);
            if (ready.isEmpty()) { // a loop: place the rest as they are
                for (String id : byId.keySet()) if (placed.add(id)) out.add(byId.get(id));
                break;
            }
            String id = ready.contains("neoforge") ? "neoforge" : ready.first();
            placed.add(id);
            out.add(byId.get(id));
        }
        return out;
    }

    private static void readAccessTransformers(ModFile f) throws IOException {
        List<String> paths = new ArrayList<>();
        for (var at : f.getModFileInfo().getConfig().getConfigList("accessTransformers"))
            at.<String>getConfigElement("file").ifPresent(paths::add);
        if (paths.isEmpty()) paths.add("META-INF/accesstransformer.cfg");
        for (String p : paths) {
            Path at = f.findResource(p);
            if (Files.isRegularFile(at)) Engine.access.add(Files.readString(at, StandardCharsets.UTF_8));
        }
    }

    private static List<String> mixinConfigs(ModFile f) {
        List<String> out = new ArrayList<>();
        for (var m : f.getModFileInfo().getConfig().getConfigList("mixins")) m.<String>getConfigElement("config").ifPresent(out::add);
        return out;
    }

    // ------------------------------------------------------------------------------------------------ coremods

    private static Map<String, List<ITransformer<?>>> coremods(ClassLoader loader) {
        Map<String, List<ITransformer<?>>> out = new HashMap<>();
        for (ICoreMod cm : ServiceLoader.load(ICoreMod.class, loader)) {
            for (ITransformer<?> t : cm.getTransformers()) {
                if (t.getTargetType() != TargetType.CLASS) { Engine.log("Skipping a coremod change of type " + t.getTargetType()); continue; }
                for (var target : t.targets()) out.computeIfAbsent(target.className().replace('/', '.'), k -> new ArrayList<>()).add(t);
            }
        }
        Engine.log("Coremods: " + out.size() + " game classes get small changes");
        return out;
    }

    @SuppressWarnings("unchecked")
    private static byte[] runCoremods(String name, byte[] bytes, List<ITransformer<?>> list) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        ITransformerVotingContext ctx = new ITransformerVotingContext() {
            public String getClassName() { return name; }
            public boolean doesClassExist() { return true; }
        };
        for (ITransformer<?> t : list) {
            ITransformer<ClassNode> ct = (ITransformer<ClassNode>) t;
            if (ct.castVote(ctx) == TransformerVoteResult.YES) node = ct.transform(node, ctx);
        }
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(w);
        return w.toByteArray();
    }

    private static void initMixinExtras(ClassLoader loader) {
        try {
            Class.forName("com.llamalad7.mixinextras.MixinExtrasBootstrap", true, loader).getMethod("init").invoke(null);
        } catch (ClassNotFoundException e) {
            // no MixinExtras around
        } catch (Exception e) {
            Engine.log("MixinExtras could not start: " + e);
        }
    }
}
