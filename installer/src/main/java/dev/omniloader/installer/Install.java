package dev.omniloader.installer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** The actual install steps. Used by both the window and the command line. */
public final class Install {
    public static final String MC = "1.21.1";
    public static final String NEOFORGE = "21.1.264";
    /** Minecraft 26.1.2: Omniloader on top of NeoForge 26.1.2 (Omniloader's own engine is 1.21.1-only for now). */
    public static final String MC26 = "26.1.2", NEOFORGE26 = "26.1.2.126";
    public static final String NAME26 = "Omniloader 26.1.2";

    public static byte[] loaderJar26() throws IOException {
        try (InputStream in = Install.class.getResourceAsStream("/payload/omniloader-26.1.jar")) {
            if (in == null) throw new IOException("Omniloader for 26.1.2 is missing from this installer");
            return in.readAllBytes();
        }
    }
    public static final String PROFILE_NAME = "Omniloader " + MC + " (Fabric, Quilt, Forge, NeoForge)";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Consumer<String> log;

    public Install(Consumer<String> log) { this.log = log; }

    // ------------------------------------------------------------------ where things live

    public static Path home() { return Path.of(System.getProperty("user.home")); }
    static boolean mac() { return System.getProperty("os.name").toLowerCase().contains("mac"); }
    static boolean windows() { return System.getProperty("os.name").toLowerCase().contains("win"); }

    static Path appData(String mac, String win, String linux) {
        if (mac()) return home().resolve("Library/Application Support").resolve(mac);
        if (windows()) return Path.of(System.getenv().getOrDefault("APPDATA", home().toString())).resolve(win);
        return home().resolve(linux);
    }

    public static Path officialDir() { return appData("minecraft", ".minecraft", ".minecraft"); }
    public static Path prismDir() { return appData("PrismLauncher", "PrismLauncher", ".local/share/PrismLauncher"); }
    public static boolean modrinthInstalled() {
        return Files.isDirectory(appData("ModrinthApp", "ModrinthApp", ".local/share/ModrinthApp"))
                || Files.isDirectory(Path.of("/Applications/Modrinth App.app"));
    }

    // ------------------------------------------------------------------ payload

    public static byte[] loaderJar() throws IOException {
        try (InputStream in = Install.class.getResourceAsStream("/payload/omniloader-" + MC + ".jar")) {
            if (in == null) throw new IOException("This installer is damaged: Omniloader itself is missing from it.");
            return in.readAllBytes();
        }
    }

    public static final String LOADER_VERSION = Installer.version();

    /** Omniloader's own engine (the loader itself). */
    public static byte[] engineJar() throws IOException {
        try (InputStream in = Install.class.getResourceAsStream("/payload/omniloader-engine.jar")) {
            if (in == null) throw new IOException("Omniloader's engine is missing from this installer");
            return in.readAllBytes();
        }
    }

    static final String ENGINE_MAIN = "dev.omniloader.engine.ClientMain";
    static final String NEO_MAVEN = "https://maven.neoforged.net/releases/";
    /** Libraries the engine needs on top of plain Minecraft (none of them is a mod loader). */
    static final String[] ENGINE_LIBS = {
            "net.neoforged:bus:8.0.5", "net.jodah:typetools:0.6.3", "com.electronwill.night-config:toml:3.8.3", "com.electronwill.night-config:core:3.8.3",
            "org.apache.maven:maven-artifact:3.8.5", "net.neoforged:mergetool:2.0.0:api", "net.fabricmc:sponge-mixin:0.16.4+mixin.0.8.7",
            "org.ow2.asm:asm:9.10.1", "org.ow2.asm:asm-commons:9.10.1", "org.ow2.asm:asm-tree:9.10.1", "org.ow2.asm:asm-util:9.10.1", "org.ow2.asm:asm-analysis:9.10.1",
            "org.antlr:antlr4-runtime:4.13.1", "net.minecraftforge:srgutils:0.4.15", "org.codehaus.plexus:plexus-utils:3.3.0",
            "com.machinezoo.noexception:noexception:1.7.1", "net.minecrell:terminalconsoleappender:1.3.0", "org.jline:jline-reader:3.20.0", "org.jline:jline-terminal:3.20.0" };
    /** NeoForge's API (used by Omniloader as a library for NeoForge and Forge mods) and its installer (to prepare it once). */
    static final String NEO_API = "net.neoforged:neoforge:" + NEOFORGE + ":universal";
    static final String NEO_INSTALLER = "net.neoforged:neoforge:" + NEOFORGE + ":installer";

    private static JsonObject mavenLib(String name) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("url", NEO_MAVEN);
        return o;
    }
    public static final String VERSION_ID = "omniloader-" + MC;

    /** Removes Omniloader jars from a mods folder (Omniloader is installed as a loader now). */
    static void removeFromMods(Path mods) throws IOException {
        if (!Files.isDirectory(mods)) return;
        try (Stream<Path> s = Files.list(mods)) {
            for (Path p : s.toList()) {
                String n = p.getFileName().toString().toLowerCase();
                if (n.startsWith("omniloader") && n.endsWith(".jar")) Files.delete(p);
            }
        }
    }

    /** Makes a mods folder with a note, without putting Omniloader in it. */
    void prepareMods(Path mods) throws IOException {
        Files.createDirectories(mods);
        removeFromMods(mods);
        writeNote(mods);
    }

    /** Puts Omniloader into a mods folder, replacing any older copy (for launchers that can't install loaders). */
    void putLoader(Path mods) throws IOException {
        Files.createDirectories(mods);
        removeFromMods(mods);
        Files.write(mods.resolve("omniloader-" + MC + ".jar"), loaderJar());
        writeNote(mods);
        log.accept("Put Omniloader into " + mods);
    }

    private void writeNote(Path mods) throws IOException {
        Path note = mods.resolve("PUT YOUR MODS HERE.txt");
        Files.writeString(note, """
                Drop mods for Minecraft %s into this folder.
                Omniloader runs Fabric, Quilt and NeoForge mods together.

                Fabric mods that need "Fabric API" are not supported yet: Omniloader
                will switch them off and tell you, instead of crashing.
                Forge mods work too (most of them; the rest are switched off with a reason).
                """.formatted(MC));
    }

    // ------------------------------------------------------------------ official launcher

    /** @return the mods folder of the new profile */
    public Path official(Path mcDir, boolean installNeoForge) throws Exception {
        Path profiles = mcDir.resolve("launcher_profiles.json");
        if (!Files.isRegularFile(profiles))
            throw new IOException("The Minecraft Launcher hasn't been set up in " + mcDir + " yet. Open it once, then run this installer again.");
        // Omniloader as its own loader version on top of plain Minecraft: Omniloader's engine starts the game.
        byte[] jar = engineJar();
        String libPath = "dev/omniloader/omniloader-engine/" + LOADER_VERSION + "/omniloader-engine-" + LOADER_VERSION + ".jar";
        Path lib = mcDir.resolve("libraries").resolve(libPath);
        Files.createDirectories(lib.getParent());
        Files.write(lib, jar);
        JsonObject v = new JsonObject();
        v.addProperty("id", VERSION_ID);
        v.addProperty("inheritsFrom", MC);
        v.addProperty("type", "release");
        v.addProperty("mainClass", ENGINE_MAIN);
        String now0 = Instant.now().toString();
        v.addProperty("time", now0);
        v.addProperty("releaseTime", now0);
        com.google.gson.JsonArray libs = new com.google.gson.JsonArray();
        JsonObject l = new JsonObject();
        l.addProperty("name", "dev.omniloader:omniloader-engine:" + LOADER_VERSION);
        JsonObject art = new JsonObject();
        art.addProperty("path", libPath);
        art.addProperty("sha1", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(jar)));
        art.addProperty("size", jar.length);
        art.addProperty("url", "");
        JsonObject dl = new JsonObject();
        dl.add("artifact", art);
        l.add("downloads", dl);
        libs.add(l);
        for (String n : ENGINE_LIBS) libs.add(mavenLib(n));
        libs.add(mavenLib(NEO_API));
        v.add("libraries", libs);
        Path vdir = mcDir.resolve("versions").resolve(VERSION_ID);
        Files.createDirectories(vdir);
        writeAtomic(vdir.resolve(VERSION_ID + ".json"), GSON.toJson(v));
        log.accept("Installed the Omniloader " + LOADER_VERSION + " loader (game version \"" + VERSION_ID + "\").");
        Path gameDir = mcDir.resolve("omniloader");
        prepareMods(gameDir.resolve("mods"));

        JsonObject root = JsonParser.parseString(Files.readString(profiles)).getAsJsonObject();
        Files.copy(profiles, mcDir.resolve("launcher_profiles.json.before-omniloader"), StandardCopyOption.REPLACE_EXISTING);
        JsonObject all = root.has("profiles") ? root.getAsJsonObject("profiles") : new JsonObject();
        root.add("profiles", all);
        JsonObject p = all.has("omniloader") ? all.getAsJsonObject("omniloader") : new JsonObject();
        String now = Instant.now().toString();
        if (!p.has("created")) p.addProperty("created", now);
        p.addProperty("lastUsed", now);
        p.addProperty("name", PROFILE_NAME);
        p.addProperty("type", "custom");
        p.addProperty("lastVersionId", VERSION_ID);
        p.addProperty("gameDir", gameDir.toString());
        p.addProperty("icon", "data:image/png;base64," + Base64.getEncoder().encodeToString(icon(128)));
        if (!p.has("javaArgs")) p.addProperty("javaArgs", "-Xmx4G -XX:+UseG1GC");
        all.add("omniloader", p);
        writeAtomic(profiles, GSON.toJson(root));
        log.accept("Added the profile \"" + PROFILE_NAME + "\" to the Minecraft Launcher.");
        return gameDir.resolve("mods");
    }

    // ------------------------------------------------------------------ Prism Launcher

    public Path prism(Path prismDir) throws IOException {
        Path inst = prismDir.resolve("instances").resolve("Omniloader");
        Files.createDirectories(inst);
        Files.writeString(inst.resolve("mmc-pack.json"), """
                {
                    "components": [
                        { "important": true, "uid": "net.minecraft", "version": "%s" },
                        { "uid": "dev.omniloader", "version": "%s", "cachedName": "Omniloader", "cachedVersion": "%s" }
                    ],
                    "formatVersion": 1
                }
                """.formatted(MC, LOADER_VERSION, LOADER_VERSION));
        // Omniloader as a Prism loader component: Omniloader's engine starts the game (no NeoForge component).
        Files.createDirectories(inst.resolve("patches"));
        JsonObject patch = new JsonObject();
        patch.addProperty("formatVersion", 1);
        patch.addProperty("uid", "dev.omniloader");
        patch.addProperty("name", "Omniloader");
        patch.addProperty("version", LOADER_VERSION);
        com.google.gson.JsonArray req = new com.google.gson.JsonArray();
        JsonObject mcReq = new JsonObject();
        mcReq.addProperty("uid", "net.minecraft");
        mcReq.addProperty("equals", MC);
        req.add(mcReq);
        patch.add("requires", req);
        patch.addProperty("mainClass", ENGINE_MAIN);
        com.google.gson.JsonArray libs = new com.google.gson.JsonArray();
        JsonObject self = new JsonObject();
        self.addProperty("name", "dev.omniloader:omniloader-engine:" + LOADER_VERSION);
        self.addProperty("MMC-hint", "local");
        libs.add(self);
        for (String n : ENGINE_LIBS) libs.add(mavenLib(n));
        patch.add("libraries", libs);
        com.google.gson.JsonArray files = new com.google.gson.JsonArray();   // downloaded, but not put on the class path
        files.add(mavenLib(NEO_API));
        files.add(mavenLib(NEO_INSTALLER));
        patch.add("mavenFiles", files);
        Files.writeString(inst.resolve("patches").resolve("dev.omniloader.json"), GSON.toJson(patch));
        Files.createDirectories(inst.resolve("libraries"));
        try (var old = Files.list(inst.resolve("libraries"))) {   // older Omniloader jars of this instance
            for (Path o : old.toList()) if (o.getFileName().toString().startsWith("omniloader-")) Files.deleteIfExists(o);
        }
        Files.write(inst.resolve("libraries").resolve("omniloader-engine-" + LOADER_VERSION + ".jar"), engineJar());
        Path cfg = inst.resolve("instance.cfg");
        if (!Files.exists(cfg)) Files.writeString(cfg, """
                [General]
                ConfigVersion=1.2
                InstanceType=OneSix
                name=Omniloader
                iconKey=omniloader
                notes=Fabric, Quilt and NeoForge mods together, through Omniloader.
                """);
        Path icons = prismDir.resolve("icons");
        Files.createDirectories(icons);
        Files.write(icons.resolve("omniloader.png"), icon(128));
        // Prism uses "minecraft" for new instances (".minecraft" for old ones).
        Path game = Files.isDirectory(inst.resolve(".minecraft")) ? inst.resolve(".minecraft") : inst.resolve("minecraft");
        prepareMods(game.resolve("mods"));
        log.accept("Made a Prism Launcher instance called \"Omniloader\" with Omniloader as its loader (just Minecraft + Omniloader). Prism downloads Minecraft itself when you press Launch.");
        return game.resolve("mods");
    }

    // ------------------------------------------------------------------ Modrinth (.mrpack)

    // ------------------------------------------------------------------ Minecraft 26.1.2

    /** Prism instance "Omniloader 26.1.2": Minecraft 26.1.2 + NeoForge 26.1.2, Omniloader in its mods folder. */
    public Path prism26(Path prismDir) throws IOException {
        Path inst = prismDir.resolve("instances").resolve("Omniloader-26.1.2");
        Files.createDirectories(inst);
        Files.writeString(inst.resolve("mmc-pack.json"), """
                {
                    "components": [
                        { "important": true, "uid": "net.minecraft", "version": "%s" },
                        { "uid": "net.neoforged", "version": "%s" }
                    ],
                    "formatVersion": 1
                }
                """.formatted(MC26, NEOFORGE26));
        Path cfg = inst.resolve("instance.cfg");
        if (!Files.exists(cfg)) Files.writeString(cfg, """
                [General]
                ConfigVersion=1.2
                InstanceType=OneSix
                name=%s
                iconKey=omniloader
                notes=Fabric, Quilt and NeoForge mods together on Minecraft 26.1.2, through Omniloader.
                """.formatted(NAME26));
        Path icons = prismDir.resolve("icons");
        Files.createDirectories(icons);
        Files.write(icons.resolve("omniloader.png"), icon(128));
        Path game = Files.isDirectory(inst.resolve(".minecraft")) ? inst.resolve(".minecraft") : inst.resolve("minecraft");
        Path mods = game.resolve("mods");
        prepareMods(mods);
        Files.write(mods.resolve("omniloader-26.1.jar"), loaderJar26());
        log.accept("Made a Prism Launcher instance called \"" + NAME26 + "\" (Minecraft 26.1.2 + NeoForge, with Omniloader). Prism downloads the game itself when you press Launch.");
        return mods;
    }

    /** Minecraft Launcher: NeoForge 26.1.2 (installed with NeoForge's installer) + a profile with Omniloader in its mods folder. */
    public Path official26(Path mcDir, boolean installNeoForge) throws Exception {
        if (!Files.isRegularFile(mcDir.resolve("launcher_profiles.json")))
            throw new IOException("The Minecraft Launcher hasn't been set up in " + mcDir + " yet. Open it once, then run this installer again.");
        String version = "neoforge-" + NEOFORGE26;
        if (Files.isRegularFile(mcDir.resolve("versions").resolve(version).resolve(version + ".json"))) log.accept("NeoForge " + NEOFORGE26 + " is already installed.");
        else if (installNeoForge) installNeoForge(mcDir, "--install-client", NEOFORGE26);
        Path gameDir = mcDir.resolve("omniloader-26.1.2");
        Path mods = gameDir.resolve("mods");
        prepareMods(mods);
        Files.write(mods.resolve("omniloader-26.1.jar"), loaderJar26());
        Path profiles = mcDir.resolve("launcher_profiles.json");
        JsonObject root = JsonParser.parseString(Files.readString(profiles)).getAsJsonObject();
        Files.copy(profiles, mcDir.resolve("launcher_profiles.json.before-omniloader"), StandardCopyOption.REPLACE_EXISTING);
        JsonObject all = root.has("profiles") ? root.getAsJsonObject("profiles") : new JsonObject();
        root.add("profiles", all);
        JsonObject p = all.has("omniloader-26") ? all.getAsJsonObject("omniloader-26") : new JsonObject();
        String now = Instant.now().toString();
        if (!p.has("created")) p.addProperty("created", now);
        p.addProperty("lastUsed", now);
        p.addProperty("name", NAME26 + " (Fabric, Quilt, NeoForge)");
        p.addProperty("type", "custom");
        p.addProperty("lastVersionId", version);
        p.addProperty("gameDir", gameDir.toString());
        p.addProperty("icon", "data:image/png;base64," + Base64.getEncoder().encodeToString(icon(128)));
        if (!p.has("javaArgs")) p.addProperty("javaArgs", "-Xmx4G -XX:+UseG1GC");
        all.add("omniloader-26", p);
        writeAtomic(profiles, GSON.toJson(root));
        log.accept("Added the profile \"" + NAME26 + "\" to the Minecraft Launcher.");
        return mods;
    }

    /** Modrinth pack for 26.1.2: Minecraft 26.1.2 + NeoForge, Omniloader in the mods folder. */
    public Path mrpack26(Path out) throws IOException {
        JsonObject index = new JsonObject();
        index.addProperty("formatVersion", 1);
        index.addProperty("game", "minecraft");
        index.addProperty("versionId", Installer.version());
        index.addProperty("name", NAME26);
        index.addProperty("summary", "Fabric, Quilt and NeoForge mods together on Minecraft " + MC26);
        index.add("files", new com.google.gson.JsonArray());
        JsonObject deps = new JsonObject();
        deps.addProperty("minecraft", MC26);
        deps.addProperty("neoforge", NEOFORGE26);
        index.add("dependencies", deps);
        Files.createDirectories(out.toAbsolutePath().getParent());
        try (OutputStream fo = Files.newOutputStream(out); ZipOutputStream z = new ZipOutputStream(fo)) {
            z.putNextEntry(new ZipEntry("modrinth.index.json"));
            z.write(GSON.toJson(index).getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            z.putNextEntry(new ZipEntry("overrides/mods/omniloader-26.1.jar"));
            z.write(loaderJar26());
            z.closeEntry();
            z.putNextEntry(new ZipEntry("overrides/icon.png"));
            z.write(icon(128));
            z.closeEntry();
        }
        log.accept("Made the Modrinth modpack file " + out);
        return out;
    }

    /** Writes a Modrinth modpack file. The Modrinth App (and Prism) can import it with a double-click. */
    public Path mrpack(Path out) throws IOException {
        JsonObject index = new JsonObject();
        index.addProperty("formatVersion", 1);
        index.addProperty("game", "minecraft");
        index.addProperty("versionId", Installer.version());
        index.addProperty("name", "Omniloader");
        index.addProperty("summary", "Fabric, Quilt and NeoForge mods together on Minecraft " + MC);
        index.add("files", new com.google.gson.JsonArray());
        JsonObject deps = new JsonObject();
        deps.addProperty("minecraft", MC);
        deps.addProperty("neoforge", NEOFORGE);
        index.add("dependencies", deps);
        Files.createDirectories(out.toAbsolutePath().getParent());
        try (OutputStream fo = Files.newOutputStream(out); ZipOutputStream z = new ZipOutputStream(fo)) {
            z.putNextEntry(new ZipEntry("modrinth.index.json"));
            z.write(GSON.toJson(index).getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            z.putNextEntry(new ZipEntry("overrides/mods/omniloader-" + MC + ".jar"));
            z.write(loaderJar());
            z.closeEntry();
            z.putNextEntry(new ZipEntry("overrides/icon.png"));
            z.write(icon(128));
            z.closeEntry();
        }
        log.accept("Made the Modrinth modpack file " + out);
        return out;
    }

    // ------------------------------------------------------------------ server

    public Path server(Path dir, boolean installNeoForge) throws Exception {
        Files.createDirectories(dir);
        if (installNeoForge) installNeoForge(dir, "--install-server");
        putLoader(dir.resolve("mods"));
        return dir.resolve("mods");
    }

    // ------------------------------------------------------------------ NeoForge

    /** Downloads NeoForge's official installer and runs it. If its quiet mode fails, opens its own window instead. */
    void installNeoForge(Path dir, String mode) throws Exception { installNeoForge(dir, mode, NEOFORGE); }

    void installNeoForge(Path dir, String mode, String NEOFORGE) throws Exception {
        String base = "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + NEOFORGE + "/neoforge-" + NEOFORGE + "-installer.jar";
        Path jar = Files.createTempFile("neoforge-installer", ".jar");
        log.accept("Downloading NeoForge " + NEOFORGE + "...");
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();
        HttpResponse<Path> r = http.send(HttpRequest.newBuilder(URI.create(base)).build(), HttpResponse.BodyHandlers.ofFile(jar));
        if (r.statusCode() != 200) throw new IOException("Could not download NeoForge (HTTP " + r.statusCode() + ")");
        String expected = http.send(HttpRequest.newBuilder(URI.create(base + ".sha1")).build(), HttpResponse.BodyHandlers.ofString()).body().trim();
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(jar)));
        if (!actual.equalsIgnoreCase(expected)) throw new IOException("The NeoForge download was damaged. Please try again.");
        String java = ProcessHandle.current().info().command().orElse("java");
        log.accept("Installing NeoForge (this takes a minute or two)...");
        Process p = new ProcessBuilder(java, "-jar", jar.toString(), mode, dir.toString()).redirectErrorStream(true).start();
        try (var in = p.inputReader()) {
            String line;
            while ((line = in.readLine()) != null) if (line.contains("Successfully") || line.contains("ERROR")) log.accept("  NeoForge: " + line.trim());
        }
        if (p.waitFor() != 0) {
            log.accept("NeoForge's quiet install didn't work, so I'm opening NeoForge's own installer. Choose \"Install client\" and press OK.");
            new ProcessBuilder(java, "-jar", jar.toString()).start().waitFor();
        }
        log.accept("NeoForge is installed.");
    }

    // ------------------------------------------------------------------ helpers

    static void writeAtomic(Path file, String text) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".omniloader-tmp");
        Files.writeString(tmp, text);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Omniloader's icon: four coloured tiles (one per loader) joined in a ring. Drawn in code, no image files needed. */
    public static byte[] icon(int size) throws IOException {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int r = size / 5;
        g.setPaint(new GradientPaint(0, 0, new Color(0x1d1f3b), size, size, new Color(0x3a1d4f)));
        g.fillRoundRect(0, 0, size, size, r, r);
        Color[] c = {new Color(0xdbb06b), new Color(0x9a5bd6), new Color(0xe06c3a), new Color(0x4fb3d9)}; // Fabric, Quilt, NeoForge, Forge
        int s = size * 30 / 100, gap = size * 6 / 100, x0 = (size - 2 * s - gap) / 2, y0 = x0;
        for (int i = 0; i < 4; i++) {
            g.setColor(c[i]);
            g.fillRoundRect(x0 + (i % 2) * (s + gap), y0 + (i / 2) * (s + gap), s, s, s / 3, s / 3);
        }
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size * 42 / 100));
        var fm = g.getFontMetrics();
        g.drawString("O", (size - fm.stringWidth("O")) / 2, (size + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", bo);
        return bo.toByteArray();
    }

    public static List<String> detected() {
        List<String> out = new java.util.ArrayList<>();
        if (Files.isRegularFile(officialDir().resolve("launcher_profiles.json"))) out.add("official");
        if (Files.isDirectory(prismDir())) out.add("prism");
        if (modrinthInstalled()) out.add("modrinth");
        return out;
    }
}
