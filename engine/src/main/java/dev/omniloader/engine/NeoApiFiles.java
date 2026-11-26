package dev.omniloader.engine;

import dev.omniloader.loader.OmniPaths;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Finds NeoForge's API files (its API jar and its changed game classes), which Omniloader uses as a library
 * to give NeoForge and Forge mods the API they expect. Launchers that ever installed NeoForge already have them;
 * if not, Omniloader makes them once with NeoForge's own installer (into Omniloader's folder, not the launcher's).
 */
final class NeoApiFiles {
    private NeoApiFiles() {}

    static NeoBoot.GameFiles ensure(boolean client) {
        for (Path root : roots()) {
            NeoBoot.GameFiles f = NeoBoot.locate(client, root);
            if (f != null) return f;
        }
        if (Boolean.getBoolean("omniloader.noNeoApi")) return null;
        try {
            Path root = install(client);
            return root == null ? null : NeoBoot.locate(client, root);
        } catch (Exception e) {
            Engine.log("Could not prepare NeoForge's API files: " + e);
            return null;
        }
    }

    /** Library folders where launchers keep files, plus Omniloader's own. */
    static List<Path> roots() {
        Set<Path> out = new LinkedHashSet<>();
        String lib = System.getProperty("libraryDirectory");
        if (lib != null) out.add(Path.of(lib));
        for (String e : System.getProperty("java.class.path").split(File.pathSeparator)) {
            String n = e.replace('\\', '/');
            int i = n.indexOf("/libraries/");
            if (i > 0) out.add(Path.of(e.substring(0, i + "/libraries".length())));
        }
        out.add(cache().resolve("client").resolve("libraries"));
        out.add(cache().resolve("server").resolve("libraries"));
        Path home = Path.of(System.getProperty("user.home"));
        String appData = System.getenv("APPDATA");
        out.add(home.resolve("Library/Application Support/PrismLauncher/libraries"));
        out.add(home.resolve("Library/Application Support/minecraft/libraries"));
        out.add(home.resolve(".local/share/PrismLauncher/libraries"));
        out.add(home.resolve(".minecraft/libraries"));
        if (appData != null) {
            out.add(Path.of(appData, "PrismLauncher", "libraries"));
            out.add(Path.of(appData, ".minecraft", "libraries"));
        }
        out.add(Engine.gameDir.resolve("libraries"));
        List<Path> list = new ArrayList<>();
        for (Path p : out) if (Files.isDirectory(p)) list.add(p);
        return list;
    }

    private static Path cache() { return OmniPaths.root().resolve("engine").resolve("neoforge-" + NeoBoot.NEO); }

    /** Runs NeoForge's installer once into Omniloader's own folder. Returns its library folder. */
    private static Path install(boolean client) throws Exception {
        String side = client ? "client" : "server";
        Path dir = cache().resolve(side);
        Files.createDirectories(dir);
        Path installer = null;
        String rel = "net/neoforged/neoforge/" + NeoBoot.NEO + "/neoforge-" + NeoBoot.NEO + "-installer.jar";
        for (Path root : roots()) if (Files.isRegularFile(root.resolve(rel))) { installer = root.resolve(rel); break; }
        if (installer == null) {
            installer = cache().resolve("neoforge-" + NeoBoot.NEO + "-installer.jar");
            if (!Files.isRegularFile(installer)) {
                Engine.log("Downloading NeoForge's installer (needed once, for the NeoForge API files)...");
                HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(30)).build();
                HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create("https://maven.neoforged.net/releases/" + rel)).build(), HttpResponse.BodyHandlers.ofInputStream());
                if (r.statusCode() != 200) throw new IllegalStateException("download failed (HTTP " + r.statusCode() + ")");
                Path tmp = Files.createTempFile(cache(), "installer", ".tmp");
                try (InputStream in = r.body()) { Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING); }
                Files.move(tmp, installer, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        if (client) {
            Path profiles = dir.resolve("launcher_profiles.json");
            if (!Files.exists(profiles)) Files.writeString(profiles, "{\"profiles\":{}}");
        }
        Engine.log("First start: preparing NeoForge's API files with NeoForge's installer (one time, about a minute)...");
        String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        Process p = new ProcessBuilder(java, "-jar", installer.toString(), client ? "--install-client" : "--install-server", dir.toString())
                .directory(dir.toFile()).redirectErrorStream(true).redirectOutput(dir.resolve("installer-output.log").toFile()).start();
        if (!p.waitFor(15, TimeUnit.MINUTES)) { p.destroyForcibly(); throw new IllegalStateException("NeoForge's installer took too long"); }
        if (p.exitValue() != 0) throw new IllegalStateException("NeoForge's installer failed (see " + dir.resolve("installer-output.log") + ")");
        Engine.log("NeoForge's API files are ready");
        return dir.resolve("libraries");
    }
}
