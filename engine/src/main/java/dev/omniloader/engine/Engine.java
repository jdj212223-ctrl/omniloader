package dev.omniloader.engine;

import dev.omniloader.loader.convert.JarConverter;
import dev.omniloader.loader.mapping.MappingLoader;
import dev.omniloader.runtime.fabric.FabricLoaderImpl;
import dev.omniloader.runtime.fabric.ModContainerImpl;
import dev.omniloader.engine.mixin.OmniMixinService;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Omniloader's own engine. Starts plain Minecraft by itself:
 *  1. translates the game to Mojang's names (once),
 *  2. loads Minecraft and mods through Omniloader's class loader,
 *  3. hooks up Mixin so mods can change the game,
 *  4. starts the mods, then Minecraft.
 * No NeoForge and no Fabric Loader involved.
 */
public final class Engine {
    public static final String VERSION = version();
    public static volatile boolean client;
    public static Path gameDir;
    public static String[] args;
    public static EngineClassLoader loader;
    public static AccessRules access;

    private Engine() {}

    static String version() {
        String v = Engine.class.getPackage() != null ? Engine.class.getPackage().getImplementationVersion() : null;
        return v != null ? v : "dev";
    }

    public static void log(String s) { System.out.println("[Omniloader] " + s); }

    public static void launch(boolean isClient, String[] arguments, String mainClass) throws Throwable {
        client = isClient;
        args = arguments;
        gameDir = Path.of(argValue(arguments, "--gameDir", ".")).toAbsolutePath().normalize();
        log("Omniloader " + VERSION + " (own engine) starting Minecraft " + dev.omniloader.loader.Platform.MC + " on the " + (client ? "client" : "server"));

        // Full mode (Fabric + Quilt + Forge + NeoForge) whenever NeoForge's API files are around
        if (!Boolean.getBoolean("omniloader.pure")) {
            NeoBoot.GameFiles neo = NeoBoot.locate(client);
            if (neo != null) { NeoBoot.launch(neo, mainClass); return; }
            log("NeoForge's API files not found: starting in Fabric/Quilt-only mode");
        }

        // 1. Minecraft, in Mojang's names
        Path gameJar = findGameJar();
        log("Game: " + gameJar);
        Path mappings = MappingLoader.mojangFile("client"); // the client jar contains the server too
        Path mcJar = GameRemapper.ensure(gameJar, mappings, gameDir.resolve(".omniloader").resolve("engine"));

        // 2. Omniloader's class loader
        loader = new EngineClassLoader(Engine.class.getClassLoader());
        loader.addJar(mcJar);
        Thread.currentThread().setContextClassLoader(loader);
        access = new AccessRules();

        // 3. mods
        List<FabricLoaderImpl.Loaded> loaded = new ArrayList<>();
        List<String> mixinConfigs = new ArrayList<>();
        Path modsDir = gameDir.resolve("mods");
        Files.createDirectories(modsDir);
        Files.createDirectories(gameDir.resolve("config")); // Fabric Loader makes this; mods expect it
        try (Stream<Path> s = Files.list(modsDir)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".jar")).sorted().toList()) {
                byte[] data = Files.readAllBytes(p);
                String disabled = null;
                var meta = JarConverter.readMeta(JarConverter.readZip(data));
                if (meta == null) { log("Skipping " + p.getFileName() + " (not a Fabric or Quilt mod; this engine runs Fabric and Quilt mods for now)"); continue; }
                for (String dep : meta.depends.keySet())
                    if (dev.omniloader.loader.OmniState.isFabricApiId(dep)) disabled = "it needs Fabric API, which the Omniloader engine doesn't include yet";
                var r = JarConverter.convert(data, client ? "client" : "server", disabled);
                loader.addJar(r.jar());
                loaded.add(new FabricLoaderImpl.Loaded(r.jar(), r.active(), r.reason()));
                try (ZipFile z = new ZipFile(r.jar().toFile())) {
                    var at = z.getEntry("META-INF/accesstransformer.cfg");
                    if (at != null && r.active()) access.add(new String(z.getInputStream(at).readAllBytes(), StandardCharsets.UTF_8));
                    var toml = z.getEntry("META-INF/neoforge.mods.toml");
                    if (toml != null && r.active()) {
                        Matcher m = Pattern.compile("config=\"([^\"]+)\"").matcher(new String(z.getInputStream(toml).readAllBytes(), StandardCharsets.UTF_8));
                        while (m.find()) mixinConfigs.add(m.group(1));
                    }
                }
                log((r.active() ? "Loading " : "Listing (switched off: " + r.reason() + ") ") + meta.loader + " mod " + meta.name + " " + meta.version);
            }
        }
        FabricLoaderImpl.INSTANCE.setup(loaded);

        // 4. Mixin
        startMixin(mixinConfigs);
        loader.setGenerator(name -> { var t = dev.omniloader.engine.mixin.OmniMixinService.transformer(); return t == null ? null : t.generateClass(org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment(), name); });
        loader.setTransformer((name, bytes) -> {
            byte[] b = access.isEmpty() ? bytes : access.transform(name.replace('.', '/'), bytes);
            var t = OmniMixinService.transformer();
            return t == null ? b : t.transformClassBytes(name, name, b);
        });
        log(mixinConfigs.size() + " mixin config(s) registered");

        // 5. Minecraft's own start-up basics, then the mods' start-up code (like Fabric: main, then client/server)
        loader.loadClass("net.minecraft.SharedConstants").getMethod("tryDetectVersion").invoke(null);
        loader.loadClass("net.minecraft.server.Bootstrap").getMethod("bootStrap").invoke(null);
        EntrypointRunner.run("main", "init");
        EntrypointRunner.run(client ? "client" : "server", client ? "client_init" : "server_init");
        long active = FabricLoaderImpl.INSTANCE.containers().stream().filter(c -> c.foreign && c.active).distinct().count();
        log(active + " mod(s) running. Starting Minecraft.");

        // 6. Minecraft
        loader.loadClass(mainClass).getMethod("main", String[].class).invoke(null, (Object) arguments);
    }

    /** Starts Mixin with Omniloader as its host and registers the mods' mixin configs. */
    static void startMixin(List<String> mixinConfigs) throws Exception {
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(client ? MixinEnvironment.Side.CLIENT : MixinEnvironment.Side.SERVER);
        for (String cfg : mixinConfigs) Mixins.addConfiguration(cfg);
        Method gotoPhase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        gotoPhase.setAccessible(true);
        gotoPhase.invoke(null, MixinEnvironment.Phase.INIT);
        gotoPhase.invoke(null, MixinEnvironment.Phase.DEFAULT);
    }

    static String argValue(String[] a, String key, String def) {
        for (int i = 0; i < a.length - 1; i++) if (a[i].equals(key)) return a[i + 1];
        return def;
    }

    /** The game jar: given explicitly, or the one on the class path that contains Minecraft. */
    static Path findGameJar() throws Exception {
        String given = System.getProperty("omniloader.gameJar");
        if (given != null) return Path.of(given);
        for (String e : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (!e.endsWith(".jar")) continue;
            try (ZipFile z = new ZipFile(e)) { if (z.getEntry("net/minecraft/client/main/Main.class") != null || z.getEntry("net/minecraft/server/Main.class") != null) return Path.of(e); }
            catch (Exception ignored) {}
        }
        throw new IllegalStateException("Can't find the Minecraft game jar (set -Domniloader.gameJar=...)");
    }
}
