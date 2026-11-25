package dev.omniloader.loader.convert;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.omniloader.loader.OmniPaths;
import dev.omniloader.loader.Platform;
import dev.omniloader.loader.mapping.MappingLoader;
import dev.omniloader.loader.mapping.Mappings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Turns a Fabric or Quilt mod jar into a jar NeoForge can load:
 *  - every Minecraft name inside is translated from Fabric's names to Mojang's,
 *  - mixin "refmaps" (name lookup tables for mixins) are translated too,
 *  - access wideners become access transformers,
 *  - a neoforge.mods.toml is written so NeoForge lists it and applies its mixins.
 * Results are cached by checksum, so each mod is only converted once.
 */
public final class JarConverter {
    private static final Logger LOG = LoggerFactory.getLogger("Omniloader");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public record Result(Path jar, ModMeta meta, boolean active, String reason) {}

    private JarConverter() {}

    public static Map<String, byte[]> readZip(byte[] data) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (!e.isDirectory()) out.put(e.getName(), z.readAllBytes());
            }
        }
        return out;
    }

    public static ModMeta readMeta(Map<String, byte[]> files) {
        if (files.containsKey("quilt.mod.json")) return ModMeta.quilt(new String(files.get("quilt.mod.json"), StandardCharsets.UTF_8));
        if (files.containsKey("fabric.mod.json")) return ModMeta.fabric(new String(files.get("fabric.mod.json"), StandardCharsets.UTF_8));
        return null;
    }

    /**
     * @param disabledReason non-null if the mod must load "switched off" (no mixins, no startup code), e.g. a missing dependency.
     */
    public static Result convert(byte[] data, String dist, String disabledReason) throws IOException {
        String key = OmniPaths.sha1(data) + "-" + dist + "-" + (disabledReason == null ? "on" : "off") + "-v" + OmniPaths.CONVERTER_VERSION;
        Map<String, byte[]> files = readZip(data);
        ModMeta meta = readMeta(files);
        if (meta == null) throw new IOException("not a Fabric or Quilt mod");

        boolean sideOk = meta.environment.equals("*") || meta.environment.equals(dist);
        String reason = disabledReason != null ? disabledReason : sideOk ? null : "it only runs on the " + meta.environment + " side";
        boolean active = reason == null;

        Path out = OmniPaths.cache().resolve(meta.neoId + "-" + key + ".jar");
        if (Files.isRegularFile(out)) return new Result(out, meta, active, reason);

        long t0 = System.nanoTime();
        // Mods built for an obfuscated Minecraft (1.21.x and older) use Fabric's "intermediary" names.
        // Mods built for 26.1+ already use Mojang's real names and need no translation.
        boolean intermediary = usesIntermediary(files);
        // Which Minecraft the mod was made for: intermediary names mean an obfuscated version (we know 1.21.1's names);
        // Mojang's own names on an obfuscated game mean 26.1 or newer.
        String builtFrom = intermediary ? MappingLoader.MC : Platform.OBFUSCATED && usesMinecraft(files) ? "26.1" : Platform.MC;
        String mcDep = meta.depends.get("minecraft");
        boolean crossVersion = !builtFrom.equals(Platform.MC) || !VersionRanges.fabricAccepts(mcDep, Platform.MC_FULL);
        java.util.Map<String, String> bridge = builtFrom.equals(Platform.MC) ? java.util.Map.of() : Bridges.load(builtFrom, Platform.MC);
        Mappings m = intermediary ? MappingLoader.get() : null;
        if (m != null && !bridge.isEmpty()) m = m.withBridge(bridge);
        IntermediaryRemapper remapper = intermediary ? new IntermediaryRemapper(m) : null;
        BridgeRemapper bridger = !intermediary && !bridge.isEmpty() ? new BridgeRemapper(bridge) : null;
        int newestJava = 0;
        for (var e : files.entrySet()) if (e.getKey().endsWith(".class")) newestJava = Math.max(newestJava, ClassVersions.java(ClassVersions.major(e.getValue())));
        boolean newerJava = newestJava > ClassVersions.java(Platform.MAX_CLASS_VERSION);

        // Which mixin configs apply on this side, and which refmaps they use.
        List<String> mixinConfigs = new ArrayList<>();
        Set<String> refmaps = new HashSet<>();
        for (ModMeta.Mixin mx : meta.mixins) {
            byte[] cfg = files.get(mx.config());
            if (cfg == null) { LOG.warn("[Omniloader] {}: mixin config {} is missing", meta.id, mx.config()); continue; }
            try {
                JsonObject o = JsonParser.parseString(new String(cfg, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("refmap")) refmaps.add(o.get("refmap").getAsString());
            } catch (Exception ex) {
                LOG.warn("[Omniloader] {}: could not read mixin config {}", meta.id, mx.config());
            }
            if (active && (mx.environment().equals("*") || mx.environment().equals(dist))) mixinConfigs.add(mx.config());
        }
        for (String n : files.keySet()) if (n.endsWith("refmap.json") && !n.contains("/")) refmaps.add(n);

        Map<String, byte[]> result = new LinkedHashMap<>();
        java.util.Set<String> refs = new java.util.TreeSet<>(), adds = new java.util.TreeSet<>();
        List<String> mixinPlugins = new ArrayList<>();
        Manifest mf = new Manifest();
        if (files.containsKey("META-INF/MANIFEST.MF")) mf.read(new ByteArrayInputStream(files.get("META-INF/MANIFEST.MF")));
        int classes = 0;
        for (var e : files.entrySet()) {
            String n = e.getKey();
            String upper = n.toUpperCase();
            if (n.equals("META-INF/MANIFEST.MF") || n.equals("META-INF/neoforge.mods.toml") || n.equals("META-INF/mods.toml")) continue;
            if (upper.startsWith("META-INF/") && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC"))) continue;
            if (n.endsWith("module-info.class")) continue;
            // A switched-off mod is only listed (name, icon): no code, recipes, textures or bundled jars.
            if (!active && !(n.equals("fabric.mod.json") || n.equals("quilt.mod.json") || n.equals(meta.icon) || n.startsWith("META-INF/") && !n.startsWith("META-INF/jars/"))) continue;
            byte[] b = e.getValue();
            if (n.endsWith(".class")) {
                try {
                    b = b.clone();
                    ClassVersions.downgrade(b, Platform.MAX_CLASS_VERSION);
                    if (remapper != null) b = remapper.remapClass(b);
                    else if (bridger != null) b = bridger.remapClass(b);
                    b = NeoFieldGetters.apply(b);
                    classes++;
                    if (active) RefCollector.collect(b, refs, adds, newerJava);
                }
                catch (Exception ex) { LOG.warn("[Omniloader] {}: could not translate {}: {}", meta.id, n, ex.toString()); }
            } else if (refmaps.contains(n) && m != null) {
                b = remapRefmap(b, m);
            } else if (active && meta.mixins.stream().anyMatch(x -> x.config().equals(n))) {
                b = gateMixinConfig(b, crossVersion || newerJava, mixinPlugins);
            }
            result.put(n, b);
        }

        String at = null;
        if (active && meta.accessWidener != null && files.containsKey(meta.accessWidener)) {
            at = AccessWidenerConverter.convert(new String(files.get(meta.accessWidener), StandardCharsets.UTF_8), m != null ? m : new Mappings());
            result.put("META-INF/accesstransformer.cfg", at.getBytes(StandardCharsets.UTF_8));
        }

        if (active) {
            result.put("META-INF/omniloader/refs.txt", String.join("\n", refs).getBytes(StandardCharsets.UTF_8));
            result.put("META-INF/omniloader/adds.txt", String.join("\n", adds).getBytes(StandardCharsets.UTF_8));
            result.put("META-INF/omniloader/mixins.txt", String.join("\n", mixinPlugins).getBytes(StandardCharsets.UTF_8));
        }
        String builtFor = mcDep != null ? mcDep : builtFrom;
        result.put("META-INF/neoforge.mods.toml", toml(meta, mixinConfigs, at != null, crossVersion ? builtFor : null).getBytes(StandardCharsets.UTF_8));
        JsonObject info = new JsonObject();
        info.addProperty("loader", meta.loader);
        info.addProperty("id", meta.id);
        info.addProperty("neoId", meta.neoId);
        info.addProperty("active", active);
        info.addProperty("crossVersion", crossVersion);
        info.addProperty("builtFor", crossVersion ? builtFor : Platform.MC);
        if (newerJava) info.addProperty("builtForJava", newestJava);
        if (reason != null) info.addProperty("reason", reason);
        JsonArray provides = new JsonArray();
        meta.provides.forEach(provides::add);
        info.add("provides", provides);
        result.put("META-INF/omniloader/mod.json", GSON.toJson(info).getBytes(StandardCharsets.UTF_8));

        Attributes a = mf.getMainAttributes();
        a.putValue("Manifest-Version", "1.0");
        a.putValue("Automatic-Module-Name", "omni." + meta.loader + "." + meta.neoId);
        a.remove(new Attributes.Name("FMLModType"));
        mf.getEntries().clear();

        Path tmp = Files.createTempFile(OmniPaths.cache(), meta.neoId, ".tmp");
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
        LOG.info("[Omniloader] Converted {} mod '{}' ({} classes, {} mixin configs{}{}) in {} ms",
                meta.loader, meta.id, classes, mixinConfigs.size(),
                crossVersion ? ", built for Minecraft " + builtFor + ": bridged to " + Platform.MC + ", may not fully work" : "",
                newerJava ? ", built for Java " + newestJava + ": relabelled for Java " + ClassVersions.java(Platform.MAX_CLASS_VERSION) : "",
                (System.nanoTime() - t0) / 1_000_000);
        return new Result(out, meta, active, reason);
    }

    /**
     * Puts Omniloader's gatekeeper plugin in front of a mod's mixins (so a switched-off mod changes nothing),
     * remembering the mod's own plugin. Mods made for another Minecraft version also get "soft" mixins:
     * one that no longer fits the game is skipped with a warning instead of stopping the whole game.
     */
    static byte[] gateMixinConfig(byte[] in, boolean soft, List<String> plugins) {
        try {
            JsonObject o = JsonParser.parseString(new String(in, StandardCharsets.UTF_8)).getAsJsonObject();
            String pkg = o.has("package") ? o.get("package").getAsString() : "";
            plugins.add(pkg + "\t" + (o.has("plugin") ? o.get("plugin").getAsString() : "-"));
            o.addProperty("plugin", "dev.omniloader.runtime.OmniMixinPlugin");
            if (soft) {
                o.addProperty("required", false);
                JsonObject inj = o.has("injectors") ? o.getAsJsonObject("injectors") : new JsonObject();
                inj.addProperty("defaultRequire", 0);
                o.add("injectors", inj);
            }
            return GSON.toJson(o).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return in;
        }
    }

    private static byte[] remapRefmap(byte[] in, Mappings m) {
        try {
            JsonElement root = JsonParser.parseString(new String(in, StandardCharsets.UTF_8));
            return GSON.toJson(mapStrings(root, m)).getBytes(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            LOG.warn("[Omniloader] could not translate a refmap: {}", ex.toString());
            return in;
        }
    }

    private static JsonElement mapStrings(JsonElement e, Mappings m) {
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) return new JsonPrimitive(m.mapText(e.getAsString()));
        if (e.isJsonArray()) {
            JsonArray a = new JsonArray();
            for (JsonElement x : e.getAsJsonArray()) a.add(mapStrings(x, m));
            return a;
        }
        if (e.isJsonObject()) {
            JsonObject o = new JsonObject();
            for (var x : e.getAsJsonObject().entrySet()) o.add(x.getKey(), mapStrings(x.getValue(), m));
            return o;
        }
        return e;
    }

    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> {}
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }

    /** True if the jar's code touches Minecraft at all. */
    static boolean usesMinecraft(Map<String, byte[]> files) {
        for (var e : files.entrySet())
            if (e.getKey().endsWith(".class") && new String(e.getValue(), StandardCharsets.ISO_8859_1).contains("net/minecraft/")) return true;
        return false;
    }

    /** True if the jar's code uses Fabric's intermediary names (made for an obfuscated Minecraft). */
    static boolean usesIntermediary(Map<String, byte[]> files) {
        for (var e : files.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            if (new String(e.getValue(), StandardCharsets.ISO_8859_1).contains("net/minecraft/class_")) return true;
        }
        for (var e : files.entrySet()) if (e.getKey().endsWith("refmap.json")) return true;
        return false;
    }

    private static String toml(ModMeta meta, List<String> mixins, boolean hasAt, String crossFrom) {
        StringBuilder t = new StringBuilder();
        t.append("# Generated by Omniloader from a ").append(meta.loader).append(" mod\n");
        t.append("modLoader=\"javafml\"\nloaderVersion=\"[1,)\"\n");
        t.append("license=").append(q(meta.license)).append('\n');
        t.append("\n[[mods]]\n");
        t.append("modId=").append(q(meta.neoId)).append('\n');
        t.append("version=").append(q(meta.version)).append('\n');
        String tag = meta.loader.equals("quilt") ? "Quilt" : "Fabric";
        t.append("displayName=").append(q(meta.name)).append('\n');
        String note = crossFrom == null ? "[" + tag + " mod, running through Omniloader]"
                : "[" + tag + " mod made for Minecraft " + crossFrom + ", bridged to " + Platform.MC + " by Omniloader. Parts of it may not work.]";
        t.append("description=").append(q(meta.description + "\n\n" + note)).append('\n');
        if (!meta.authors.isEmpty()) t.append("authors=").append(q(String.join(", ", meta.authors))).append('\n');
        if (meta.icon != null) t.append("logoFile=").append(q(meta.icon)).append('\n');
        for (String mx : mixins) t.append("\n[[mixins]]\nconfig=").append(q(mx)).append('\n');
        if (hasAt) t.append("\n[[accessTransformers]]\nfile=\"META-INF/accesstransformer.cfg\"\n");
        return t.toString();
    }
}
