package dev.omniloader.loader.convert;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.omniloader.loader.OmniPaths;
import dev.omniloader.loader.Platform;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * NeoForge mods (and libraries) made for a different Minecraft version or a newer Java.
 *  - class files are relabelled so this game's Java accepts them,
 *  - Minecraft renames between the versions are applied where Omniloader knows them,
 *  - the mod's version limits are relaxed so NeoForge lets it in,
 *  - its start-up class is held back: Omniloader starts it only if everything it uses exists in this game,
 *  - its mixins go through Omniloader's gatekeeper and can't crash the game if they no longer fit.
 */
public final class NeoJarConverter {
    private static final Logger LOG = LoggerFactory.getLogger("Omniloader");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final String MOD = "Lnet/neoforged/fml/common/Mod;";
    private static final String SUBSCRIBER = "Lnet/neoforged/fml/common/EventBusSubscriber;";
    private static final Pattern DEP_BLOCK = Pattern.compile("(?ms)^\\s*\\[\\[dependencies\\.[^\\]]+\\]\\](.*?)(?=^\\s*\\[|\\z)");
    private static final Pattern MOD_ID = Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern RANGE = Pattern.compile("versionRange\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern MIXIN_CFG = Pattern.compile("(?ms)\\[\\[mixins\\]\\].*?config\\s*=\\s*\"([^\"]+)\"");

    private NeoJarConverter() {}

    /** Quick look: does this jar need converting? */
    public record Check(boolean needed, boolean mod, String mcRange, int newestJava) {}

    public static Check check(String toml, int newestJava) {
        String mc = null, neo = null;
        if (toml != null) {
            Matcher b = DEP_BLOCK.matcher(toml);
            while (b.find()) {
                String block = b.group(1);
                Matcher id = MOD_ID.matcher(block), r = RANGE.matcher(block);
                if (!id.find() || !r.find()) continue;
                if (id.group(1).equals("minecraft")) mc = r.group(1);
                if (id.group(1).equals("neoforge")) neo = r.group(1);
            }
        }
        boolean otherMc = !VersionRanges.mavenAccepts(mc, Platform.MC_FULL) || !VersionRanges.mavenAccepts(neo, Platform.NEO_VERSION);
        boolean newerJava = newestJava > ClassVersions.java(Platform.MAX_CLASS_VERSION);
        return new Check(otherMc || newerJava, toml != null, mc != null ? mc : neo, newestJava);
    }

    /** Lowest version a range allows: "[26.1.2,26.2)" -> "26.1.2". */
    static String lowest(String range) {
        if (range == null) return null;
        Matcher m = Pattern.compile("[0-9]+(\\.[0-9]+)+").matcher(range);
        return m.find() ? m.group() : null;
    }

    /** The bridge version family a Minecraft version belongs to. */
    static String family(String v) {
        if (v == null) return Platform.MC;
        if (v.startsWith("26.")) return "26.1";
        if (v.startsWith("1.21") || v.startsWith("21.")) return "1.21.1";
        return v;
    }

    public static Path convert(byte[] data, Check check) throws IOException {
        String key = OmniPaths.sha1(data) + "-" + Platform.MC + "-v" + OmniPaths.CONVERTER_VERSION;
        Map<String, byte[]> files = JarConverter.readZip(data);
        String toml = check.mod() ? new String(files.get("META-INF/neoforge.mods.toml"), StandardCharsets.UTF_8) : null;
        String modId = null;
        if (toml != null) {
            Matcher m = Pattern.compile("(?ms)\\[\\[mods\\]\\].*?modId\\s*=\\s*\"([^\"]+)\"").matcher(toml);
            if (m.find()) modId = m.group(1);
        }
        String name = modId != null ? modId : "library";
        Path out = OmniPaths.cache().resolve(name + "-" + key + ".jar");
        if (Files.isRegularFile(out)) return out;

        String builtFor = lowest(check.mcRange());
        String from = family(builtFor);
        boolean otherMc = !VersionRanges.mavenAccepts(check.mcRange(), Platform.MC_FULL);
        Map<String, String> bridge = !from.equals(Platform.MC) ? Bridges.load(from, Platform.MC) : Map.of();
        BridgeRemapper bridger = bridge.isEmpty() ? null : new BridgeRemapper(bridge);
        boolean newerJava = check.newestJava() > ClassVersions.java(Platform.MAX_CLASS_VERSION);

        Set<String> mixinConfigs = new HashSet<>();
        if (toml != null) { Matcher m = MIXIN_CFG.matcher(toml); while (m.find()) mixinConfigs.add(m.group(1)); }

        Set<String> refs = new TreeSet<>(), adds = new TreeSet<>();
        List<String> mixinPlugins = new ArrayList<>();
        JsonArray entries = new JsonArray(), subscribers = new JsonArray();
        Map<String, byte[]> result = new LinkedHashMap<>();
        Manifest mf = new Manifest();
        if (files.containsKey("META-INF/MANIFEST.MF")) mf.read(new ByteArrayInputStream(files.get("META-INF/MANIFEST.MF")));
        int classes = 0;
        for (var e : files.entrySet()) {
            String n = e.getKey(), u = n.toUpperCase();
            if (n.equals("META-INF/MANIFEST.MF")) continue;
            if (u.startsWith("META-INF/") && (u.endsWith(".SF") || u.endsWith(".RSA") || u.endsWith(".DSA") || u.endsWith(".EC"))) continue;
            byte[] b = e.getValue();
            if (n.endsWith(".class") && !n.startsWith("META-INF/versions/")) {
                b = b.clone();
                ClassVersions.downgrade(b, Platform.MAX_CLASS_VERSION);
                if (bridger != null) b = bridger.remapClass(b);
                if (toml != null) b = holdBack(b, entries, subscribers);
                RefCollector.collect(b, refs, adds, newerJava);
                classes++;
            } else if (mixinConfigs.contains(n)) {
                b = JarConverter.gateMixinConfig(b, true, mixinPlugins);
            } else if (n.equals("META-INF/neoforge.mods.toml")) {
                b = relax(toml).getBytes(StandardCharsets.UTF_8);
            }
            result.put(n, b);
        }
        if (toml != null) {
            JsonObject info = new JsonObject();
            info.addProperty("loader", "neoforge");
            info.addProperty("id", modId);
            info.addProperty("neoId", modId);
            info.addProperty("active", true);
            info.addProperty("crossVersion", otherMc);
            info.addProperty("builtFor", builtFor != null ? builtFor : Platform.MC);
            if (newerJava) info.addProperty("builtForJava", check.newestJava());
            info.add("entries", entries);
            info.add("subscribers", subscribers);
            result.put("META-INF/omniloader/mod.json", GSON.toJson(info).getBytes(StandardCharsets.UTF_8));
            result.put("META-INF/omniloader/refs.txt", String.join("\n", refs).getBytes(StandardCharsets.UTF_8));
            result.put("META-INF/omniloader/adds.txt", String.join("\n", adds).getBytes(StandardCharsets.UTF_8));
            result.put("META-INF/omniloader/mixins.txt", String.join("\n", mixinPlugins).getBytes(StandardCharsets.UTF_8));
        }
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        mf.getEntries().clear();
        Path tmp = Files.createTempFile(OmniPaths.cache(), name, ".tmp");
        try (OutputStream fo = Files.newOutputStream(tmp); JarOutputStream jo = new JarOutputStream(fo, mf)) {
            Set<String> dirs = new HashSet<>();
            for (var e : result.entrySet()) {
                String n = e.getKey();
                for (int i = n.indexOf('/'); i > 0; i = n.indexOf('/', i + 1)) {
                    String d = n.substring(0, i + 1);
                    if (dirs.add(d)) { jo.putNextEntry(new ZipEntry(d)); jo.closeEntry(); }
                }
                jo.putNextEntry(new ZipEntry(n));
                jo.write(e.getValue());
                jo.closeEntry();
            }
        }
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        LOG.info("[Omniloader] Converted NeoForge {} '{}' ({} classes{}{})", toml != null ? "mod" : "library", name, classes,
                otherMc ? ", made for Minecraft " + builtFor + ": bridged to " + Platform.MC : "",
                newerJava ? ", made for Java " + check.newestJava() + ": relabelled for Java " + ClassVersions.java(Platform.MAX_CLASS_VERSION) : "");
        return out;
    }

    /** Let NeoForge accept the mod: drop its Minecraft/NeoForge/loader version limits (Omniloader checks fit itself). */
    static String relax(String toml) {
        String t = toml.replaceAll("loaderVersion\\s*=\\s*\"[^\"]*\"", "loaderVersion=\"[1,)\"");
        // Newer NeoForge lets mods leave these out; older NeoForge needs them.
        if (!t.matches("(?s).*(^|\\n)\\s*modLoader\\s*=.*")) t = "modLoader=\"javafml\"\n" + t;
        if (!t.matches("(?s).*(^|\\n)\\s*loaderVersion\\s*=.*")) t = "loaderVersion=\"[1,)\"\n" + t;
        Matcher b = DEP_BLOCK.matcher(t);
        StringBuilder sb = new StringBuilder();
        while (b.find()) {
            String block = b.group();
            Matcher id = MOD_ID.matcher(block);
            if (id.find() && (id.group(1).equals("minecraft") || id.group(1).equals("neoforge")))
                block = RANGE.matcher(block).replaceAll("versionRange=\"[0,)\"");
            b.appendReplacement(sb, Matcher.quoteReplacement(block));
        }
        b.appendTail(sb);
        return sb.toString();
    }

    /** Removes @Mod / @EventBusSubscriber (NeoForge would start those right away) and records them for Omniloader. */
    private static byte[] holdBack(byte[] cls, JsonArray entries, JsonArray subscribers) {
        String text = new String(cls, StandardCharsets.ISO_8859_1);
        if (!text.contains("net/neoforged/fml/common/Mod") && !text.contains("net/neoforged/fml/common/EventBusSubscriber")) return cls;
        ClassReader cr = new ClassReader(cls);
        ClassWriter cw = new ClassWriter(0);
        String cname = cr.getClassName().replace('/', '.');
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (!desc.equals(MOD) && !desc.equals(SUBSCRIBER)) return super.visitAnnotation(desc, visible);
                JsonObject o = new JsonObject();
                o.addProperty("class", cname);
                JsonArray dists = new JsonArray();
                o.add("dist", dists);
                (desc.equals(MOD) ? entries : subscribers).add(o);
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override public void visit(String n, Object v) { o.addProperty(n, String.valueOf(v instanceof Type t ? t.getClassName() : v)); }
                    @Override public void visitEnum(String n, String d, String v) { o.addProperty(n, v); }
                    @Override public AnnotationVisitor visitArray(String n) {
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override public void visitEnum(String x, String d, String v) { if (n.equals("value") || n.equals("dist")) dists.add(v); }
                            @Override public void visit(String x, Object v) {}
                        };
                    }
                };
            }
        }, 0);
        return cw.toByteArray();
    }
}
