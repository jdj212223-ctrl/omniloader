package dev.omniloader.loader.mapping;

import dev.omniloader.loader.OmniPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

/**
 * Builds the intermediary -> Mojang name table.
 *
 * Fabric's intermediary names are CC0 and ship inside our jar. Mojang's own names may not be
 * redistributed unmodified, so we fetch them from Mojang (once) or reuse a copy already on disk,
 * then save only the combined table.
 */
public final class MappingLoader {
    private static final Logger LOG = LoggerFactory.getLogger("Omniloader");
    public static final String MC = "1.21.1";
    private static final String CLIENT_URL = "https://piston-data.mojang.com/v1/objects/2244b6f072256667bcd9a73df124d6c58de77992/client.txt";
    private static final String CLIENT_SHA1 = "2244b6f072256667bcd9a73df124d6c58de77992";
    private static final String SERVER_URL = "https://piston-data.mojang.com/v1/objects/03f8985492bda0afc0898465341eb0acef35f570/server.txt";
    private static final String SERVER_SHA1 = "03f8985492bda0afc0898465341eb0acef35f570";

    private static Mappings cached;

    private MappingLoader() {}

    public static synchronized Mappings get() {
        if (cached != null) return cached;
        Path table = OmniPaths.mappings().resolve("intermediary-to-mojang-" + MC + ".txt");
        try {
            if (!Files.isRegularFile(table)) build(table);
            cached = read(table);
            System.setProperty("omniloader.mappings", table.toAbsolutePath().toString());
            LOG.info("[Omniloader] Name table ready: {} classes, {} methods, {} fields", cached.classes.size(), cached.methods.size(), cached.fields.size());
            return cached;
        } catch (IOException e) {
            throw new RuntimeException("Omniloader could not prepare the Minecraft name table", e);
        }
    }

    public static Path tablePath() {
        get();
        return Path.of(System.getProperty("omniloader.mappings"));
    }

    private static Mappings read(Path table) throws IOException {
        Mappings m = new Mappings();
        try (BufferedReader r = Files.newBufferedReader(table)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                switch (p[0]) {
                    case "c" -> { m.classesObf.put(p[2], p[1]); m.classes.put(p[2], p[3]); }
                    case "m" -> m.methods.put(p[1], p[2]);
                    case "f" -> m.fields.put(p[1], p[2]);
                    default -> {}
                }
            }
        }
        return m;
    }

    // ---------------------------------------------------------------- building

    private record Member(String owner, String name, String desc) {}

    private static void build(Path out) throws IOException {
        LOG.info("[Omniloader] First start: building the Fabric -> Mojang name table (one time only)...");
        // obf -> mojang
        Map<String, String> obfToNamedClass = new HashMap<>();
        Map<Member, String> obfToNamedMember = new HashMap<>();
        for (Path p : List.of(mojangFile("client", CLIENT_URL, CLIENT_SHA1), mojangFile("server", SERVER_URL, SERVER_SHA1))) {
            readProguard(p, obfToNamedClass, obfToNamedMember);
        }

        Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
        int missing = 0;
        try (InputStream raw = MappingLoader.class.getResourceAsStream("/intermediary-" + MC + ".jar");
             JarInputStream jar = new JarInputStream(raw);
             Writer w = Files.newBufferedWriter(tmp)) {
            ZipEntry e;
            while ((e = jar.getNextEntry()) != null && !e.getName().equals("mappings/mappings.tiny")) {}
            if (e == null) throw new IOException("intermediary mappings missing from Omniloader jar");
            BufferedReader r = new BufferedReader(new InputStreamReader(jar, StandardCharsets.UTF_8));
            String header = r.readLine();
            if (!header.startsWith("tiny\t2\t0\tofficial\tintermediary")) throw new IOException("unexpected intermediary format: " + header);
            String obfClass = null;
            Map<String, String> seenM = new HashMap<>(), seenF = new HashMap<>();
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("c\t")) {
                    String[] p = line.split("\t");
                    obfClass = p[1];
                    String named = obfToNamedClass.get(obfClass);
                    if (named == null) { missing++; named = p[2]; }
                    w.write("c\t" + obfClass + "\t" + p[2] + "\t" + named + "\n");
                } else if (line.startsWith("\tm\t") || line.startsWith("\tf\t")) {
                    String[] p = line.substring(1).split("\t");
                    boolean method = p[0].equals("m");
                    String named = obfToNamedMember.get(new Member(obfClass, p[2], method ? p[1] : "")) ;
                    if (named == null && !method) named = obfToNamedMember.get(new Member(obfClass, p[2], "?"));
                    if (named == null) continue;
                    Map<String, String> seen = method ? seenM : seenF;
                    String prev = seen.putIfAbsent(p[3], named);
                    if (prev == null) w.write((method ? "m\t" : "f\t") + p[3] + "\t" + named + "\n");
                }
            }
        }
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        if (missing > 0) LOG.warn("[Omniloader] {} classes had no Mojang name", missing);
    }

    /** Reads a Mojang (ProGuard-format) mapping file into obf->named tables. */
    private static void readProguard(Path file, Map<String, String> classes, Map<Member, String> members) throws IOException {
        // Pass 1: classes (needed to turn readable types into obfuscated descriptors)
        Map<String, String> namedToObf = new HashMap<>();
        List<String> lines = Files.readAllLines(file);
        for (String l : lines) {
            if (l.startsWith("#") || l.startsWith(" ") || l.isBlank()) continue;
            int a = l.indexOf(" -> ");
            String named = l.substring(0, a).replace('.', '/');
            String obf = l.substring(a + 4, l.length() - 1).replace('.', '/');
            classes.put(obf, named);
            namedToObf.put(named, obf);
        }
        String owner = null;
        for (String l : lines) {
            if (l.startsWith("#") || l.isBlank()) continue;
            if (!l.startsWith(" ")) {
                owner = l.substring(l.indexOf(" -> ") + 4, l.length() - 1).replace('.', '/');
                continue;
            }
            String s = l.trim();
            if (s.startsWith("#")) continue;
            int arrow = s.indexOf(" -> ");
            String obfName = s.substring(arrow + 4);
            String left = s.substring(0, arrow);
            // strip line numbers "12:34:"
            while (!left.isEmpty() && Character.isDigit(left.charAt(0))) left = left.substring(left.indexOf(':') + 1);
            int sp = left.indexOf(' ');
            String type = left.substring(0, sp);
            String rest = left.substring(sp + 1);
            int paren = rest.indexOf('(');
            if (paren >= 0) {
                String name = rest.substring(0, paren);
                String args = rest.substring(paren + 1, rest.indexOf(')'));
                StringBuilder d = new StringBuilder("(");
                if (!args.isEmpty()) for (String a : args.split(",")) d.append(desc(a, namedToObf));
                d.append(')').append(desc(type, namedToObf));
                members.put(new Member(owner, obfName, d.toString()), name);
            } else {
                members.put(new Member(owner, obfName, ""), rest);
                // fields: intermediary gives a descriptor; key without it, fall back if ambiguous
                members.putIfAbsent(new Member(owner, obfName, "?"), rest);
            }
        }
    }

    private static String desc(String t, Map<String, String> namedToObf) {
        int dims = 0;
        while (t.endsWith("[]")) { dims++; t = t.substring(0, t.length() - 2); }
        String base = switch (t) {
            case "void" -> "V"; case "boolean" -> "Z"; case "byte" -> "B"; case "char" -> "C";
            case "short" -> "S"; case "int" -> "I"; case "long" -> "J"; case "float" -> "F"; case "double" -> "D";
            default -> { String n = t.replace('.', '/'); yield "L" + namedToObf.getOrDefault(n, n) + ";"; }
        };
        return "[".repeat(dims) + base;
    }

    /** Finds Mojang's mapping file on disk, or downloads it once. Verified by checksum. */
    /** Mojang's name list for the client ("client") or server ("server"), found on disk or downloaded once. */
    public static Path mojangFile(String side) throws IOException {
        return side.equals("client") ? mojangFile(side, CLIENT_URL, CLIENT_SHA1) : mojangFile(side, SERVER_URL, SERVER_SHA1);
    }

    private static Path mojangFile(String side, String url, String sha1) throws IOException {
        Path target = OmniPaths.mappings().resolve("mojang-" + side + "-" + MC + ".txt");
        if (Files.isRegularFile(target) && sha1.equals(OmniPaths.sha1(target))) return target;

        List<Path> local = new ArrayList<>();
        String libDir = System.getProperty("libraryDirectory");
        if (libDir != null) {
            Path base = Path.of(libDir).resolve("net/minecraft/" + side);
            if (Files.isDirectory(base)) try (var s = Files.walk(base, 2)) {
                s.filter(p -> p.getFileName().toString().endsWith("-mappings.txt")).forEach(local::add);
            }
        }
        Path home = Path.of(System.getProperty("user.home"));
        local.add(home.resolve(".gradle/caches/neoformruntime/artifacts/minecraft_" + MC + "_" + side + "_mappings.txt"));
        for (Path p : local) {
            if (Files.isRegularFile(p) && sha1.equals(OmniPaths.sha1(p))) {
                Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
                LOG.info("[Omniloader] Using Mojang {} names from {}", side, p);
                return target;
            }
        }
        LOG.info("[Omniloader] Downloading Mojang {} names from Mojang...", side);
        try {
            HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<Path> r = c.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).build(),
                    HttpResponse.BodyHandlers.ofFile(target));
            if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        if (!sha1.equals(OmniPaths.sha1(target))) {
            Files.deleteIfExists(target);
            throw new IOException("Mojang " + side + " mappings download was corrupted");
        }
        return target;
    }
}
