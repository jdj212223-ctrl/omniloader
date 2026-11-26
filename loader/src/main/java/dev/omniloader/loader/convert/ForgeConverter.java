package dev.omniloader.loader.convert;

import dev.omniloader.loader.OmniPaths;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Forge mods: translates Forge's API names to NeoForge's (NeoForge began as a copy of Forge, so most parts have a twin),
 * points the rest at Omniloader's Forge-shaped stand-ins, turns mods.toml into neoforge.mods.toml,
 * then hands the result to the same hold-back + safety check used for other adapted NeoForge mods.
 */
public final class ForgeConverter {
    private ForgeConverter() {}

    private static Map<String, String> table;

    /** The Forge -> NeoForge class table for this Minecraft version, or null if this edition has none. */
    static synchronized Map<String, String> table() {
        if (table != null) return table.isEmpty() ? null : table;
        table = new HashMap<>();
        try (InputStream in = ForgeConverter.class.getResourceAsStream("/forge-to-neoforge.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String l;
                while ((l = r.readLine()) != null) {
                    String[] p = l.trim().split("\\s+");
                    if (p.length == 3 && p[0].equals("c")) table.put(p[1], p[2]);
                }
            }
        } catch (IOException ignored) {}
        return table.isEmpty() ? null : table;
    }

    public static boolean supported() { return table() != null; }

    public static Path convert(byte[] original, int newestJava) throws IOException {
        Map<String, byte[]> files = JarConverter.readZip(original);
        BridgeRemapper renamer = new BridgeRemapper(table());
        Map<String, byte[]> out = new LinkedHashMap<>();
        Manifest mf = new Manifest();
        if (files.containsKey("META-INF/MANIFEST.MF")) mf.read(new ByteArrayInputStream(files.get("META-INF/MANIFEST.MF")));
        String mixinConfigs = mf.getMainAttributes().getValue("MixinConfigs");
        String toml = new String(files.get("META-INF/mods.toml"), StandardCharsets.UTF_8);
        java.util.Set<String> dropped = dropProvidedLibraries(files);
        for (var e : files.entrySet()) {
            String n = e.getKey();
            if (n.equals("META-INF/MANIFEST.MF") || n.equals("META-INF/mods.toml") || dropped.contains(n)) continue;
            byte[] b = e.getValue();
            if (n.endsWith(".class") && !n.startsWith("META-INF/versions/")) {
                b = b.clone();
                ClassVersions.downgrade(b, dev.omniloader.loader.Platform.MAX_CLASS_VERSION);
                b = Backporter.applyForge(renamer.remapClass(b));
            }
            if (n.equals("META-INF/accesstransformer.cfg")) b = realNamesInAccessTransformer(new String(b, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
            out.put(n, b);
        }
        out.put("META-INF/neoforge.mods.toml", neoToml(toml, mixinConfigs, out.keySet()).getBytes(StandardCharsets.UTF_8));
        // Forge mods' own nested-jar list (jarjar) works the same on NeoForge; MixinConfigs moved into the toml.
        mf.getMainAttributes().remove(new Attributes.Name("MixinConfigs"));
        mf.getMainAttributes().putValue("Manifest-Version", "1.0");
        mf.getEntries().clear();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (JarOutputStream jo = new JarOutputStream(bo, mf)) {
            for (var e : out.entrySet()) {
                ZipEntry z = new ZipEntry(e.getKey());
                z.setTime(0); // stable output
                jo.putNextEntry(z);
                jo.write(e.getValue());
                jo.closeEntry();
            }
        }
        String mcRange = NeoJarConverter.check(new String(out.get("META-INF/neoforge.mods.toml"), StandardCharsets.UTF_8), newestJava).mcRange();
        var check = new NeoJarConverter.Check(true, true, mcRange, newestJava);
        return NeoJarConverter.convert(bo.toByteArray(), check, OmniPaths.sha1(original), "forge", missingDependencies(toml));
    }

    private static final java.util.regex.Pattern DEP = java.util.regex.Pattern.compile("(?ms)^\\s*\\[\\[dependencies\\.[^\\]]+\\]\\](.*?)(?=^\\s*\\[|\\z)");

    private static final java.util.regex.Pattern SRG_LINE = java.util.regex.Pattern.compile("^(\\s*\\S+\\s+\\S+\\s+)([mf]_\\d+_)(\\S*)(\\s*#\\s*([A-Za-z_$][A-Za-z0-9_$]*).*)$");

    /**
     * Forge access transformers often name game members by Forge's old numbers ("m_274409_") with the real
     * name in a comment ("#parameters"). Minecraft 1.21.1 runs with real names, so use those.
     */
    static String realNamesInAccessTransformer(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            java.util.regex.Matcher m = SRG_LINE.matcher(line);
            sb.append(m.matches() ? m.group(1) + m.group(5) + m.group(3) + m.group(4) : line).append('\n');
        }
        return sb.substring(0, sb.length() - 1);
    }

    /** Required mods that aren't installed: the Forge mod is switched off with a clear reason instead of NeoForge stopping the game. */
    static String missingDependencies(String toml) {
        java.util.List<String> missing = new java.util.ArrayList<>();
        boolean client = dev.omniloader.loader.Platform.isClient();
        java.util.regex.Matcher m = DEP.matcher(toml);
        while (m.find()) {
            String b = m.group(1);
            java.util.regex.Matcher id = java.util.regex.Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"").matcher(b);
            if (!id.find()) continue;
            String mod = id.group(1);
            boolean required = b.matches("(?s).*mandatory\\s*=\\s*true.*") || b.matches("(?s).*type\\s*=\\s*\"(?i:required)\".*");
            String side = b.matches("(?s).*side\\s*=\\s*\"CLIENT\".*") ? "CLIENT" : b.matches("(?s).*side\\s*=\\s*\"SERVER\".*") ? "SERVER" : "BOTH";
            if (!required || side.equals("CLIENT") && !client || side.equals("SERVER") && client) continue;
            if (mod.equals("minecraft") || mod.equals("forge") || mod.equals("neoforge")) continue;
            if (dev.omniloader.loader.OmniState.presentIds.contains(mod)) continue;
            if (!missing.contains(mod)) missing.add(mod);
        }
        return missing.isEmpty() ? null : "it needs " + String.join(", ", missing) + ", which " + (missing.size() == 1 ? "isn't" : "aren't") + " installed";
    }

    /** Libraries a Forge mod bundles that NeoForge already provides (two copies stop Java from starting). */
    static java.util.Set<String> dropProvidedLibraries(Map<String, byte[]> files) {
        java.util.Set<String> drop = new java.util.HashSet<>();
        byte[] meta = files.get("META-INF/jarjar/metadata.json");
        if (meta == null) return drop;
        try {
            var root = com.google.gson.JsonParser.parseString(new String(meta, StandardCharsets.UTF_8)).getAsJsonObject();
            var keep = new com.google.gson.JsonArray();
            for (var el : root.getAsJsonArray("jars")) {
                var j = el.getAsJsonObject();
                String artifact = j.has("identifier") ? j.getAsJsonObject("identifier").toString().toLowerCase() : "";
                if (artifact.contains("mixinextras")) drop.add(j.get("path").getAsString());
                else keep.add(j);
            }
            if (!drop.isEmpty()) {
                root.add("jars", keep);
                files.put("META-INF/jarjar/metadata.json", root.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
        return drop;
    }

    /** Forge's mods.toml -> NeoForge's: "forge" dependency becomes "neoforge", mixins move in from the manifest. */
    static String neoToml(String toml, String mixinConfigs, java.util.Set<String> jarFiles) {
        String t = toml.replaceAll("(modId\\s*=\\s*)\"forge\"", "$1\"neoforge\"");
        // Forge reads mixins from the manifest only; a [[mixins]] list in its mods.toml is ignored there (and sometimes wrong).
        t = t.replaceAll("(?ms)^\\s*\\[\\[mixins\\]\\].*?(?=^\\s*\\[|\\z)", "");
        // Forge: mandatory=true/false. NeoForge: type="required"/"optional" (and assumes "required" when missing).
        java.util.regex.Matcher m = DEP.matcher(t);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String block = m.group();
            if (!block.matches("(?s).*\\btype\\s*=.*")) {
                block = block.replaceAll("(?m)^(\\s*)mandatory\\s*=\\s*true", "$1type=\"required\"")
                             .replaceAll("(?m)^(\\s*)mandatory\\s*=\\s*false", "$1type=\"optional\"");
            }
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(block));
        }
        m.appendTail(out);
        t = out.toString();
        // Forge loads META-INF/accesstransformer.cfg on its own; NeoForge only if the toml lists it.
        if (jarFiles.contains("META-INF/accesstransformer.cfg") && !t.contains("[[accessTransformers]]"))
            t = t + "\n\n[[accessTransformers]]\nfile=\"META-INF/accesstransformer.cfg\"\n";
        if (mixinConfigs != null) {
            StringBuilder sb = new StringBuilder(t).append('\n');
            for (String c : mixinConfigs.split(",")) {
                c = c.trim();
                if (!c.isEmpty() && jarFiles.contains(c)) sb.append("\n[[mixins]]\nconfig=\"").append(c).append("\"\n");
            }
            t = sb.toString();
        }
        return t;
    }
}
