package dev.omniloader.loader.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The bits of a fabric.mod.json / quilt.mod.json that the converter needs. */
public final class ModMeta {
    public String loader;        // "fabric" or "quilt"
    public String id;            // original id
    public String neoId;         // id that NeoForge accepts
    public String version = "0.0.0";
    public String name;
    public String description = "";
    public String license = "Unknown";
    public String icon;
    public String environment = "*";
    public final List<String> authors = new ArrayList<>();
    public final List<Mixin> mixins = new ArrayList<>();
    public final List<String> nestedJars = new ArrayList<>();
    public final Map<String, String> depends = new LinkedHashMap<>();
    public final List<String> provides = new ArrayList<>();
    public String accessWidener;

    public record Mixin(String config, String environment) {}

    public static String neoId(String id) {
        String s = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (s.isEmpty() || !Character.isLetter(s.charAt(0))) s = "m_" + s;
        if (s.length() < 2) s = s + "_";
        if (s.length() > 64) s = s.substring(0, 64);
        return s;
    }

    public static ModMeta fabric(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        ModMeta m = new ModMeta();
        m.loader = "fabric";
        m.id = o.get("id").getAsString();
        m.neoId = neoId(m.id);
        if (o.has("version")) m.version = o.get("version").getAsString();
        m.name = o.has("name") ? o.get("name").getAsString() : m.id;
        if (o.has("description")) m.description = o.get("description").getAsString();
        if (o.has("license")) m.license = flat(o.get("license"));
        if (o.has("icon")) m.icon = o.get("icon").isJsonPrimitive() ? o.get("icon").getAsString() : largestIcon(o.getAsJsonObject("icon"));
        if (o.has("environment")) m.environment = o.get("environment").getAsString();
        if (o.has("authors")) for (JsonElement a : o.getAsJsonArray("authors")) m.authors.add(a.isJsonPrimitive() ? a.getAsString() : a.getAsJsonObject().get("name").getAsString());
        if (o.has("mixins")) for (JsonElement e : o.getAsJsonArray("mixins")) {
            if (e.isJsonPrimitive()) m.mixins.add(new Mixin(e.getAsString(), "*"));
            else m.mixins.add(new Mixin(e.getAsJsonObject().get("config").getAsString(),
                    e.getAsJsonObject().has("environment") ? e.getAsJsonObject().get("environment").getAsString() : "*"));
        }
        if (o.has("jars")) for (JsonElement e : o.getAsJsonArray("jars")) m.nestedJars.add(e.getAsJsonObject().get("file").getAsString());
        if (o.has("depends")) for (var e : o.getAsJsonObject("depends").entrySet()) m.depends.put(e.getKey(), flat(e.getValue()));
        if (o.has("provides")) for (JsonElement e : o.getAsJsonArray("provides")) m.provides.add(e.getAsString());
        if (o.has("accessWidener")) m.accessWidener = o.get("accessWidener").getAsString();
        return m;
    }

    public static ModMeta quilt(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject q = root.getAsJsonObject("quilt_loader");
        ModMeta m = new ModMeta();
        m.loader = "quilt";
        m.id = q.get("id").getAsString();
        m.neoId = neoId(m.id);
        if (q.has("version")) m.version = q.get("version").getAsString();
        JsonObject md = q.has("metadata") ? q.getAsJsonObject("metadata") : new JsonObject();
        m.name = md.has("name") ? md.get("name").getAsString() : m.id;
        if (md.has("description")) m.description = md.get("description").getAsString();
        if (md.has("license")) m.license = flat(md.get("license"));
        if (md.has("icon")) m.icon = md.get("icon").isJsonPrimitive() ? md.get("icon").getAsString() : largestIcon(md.getAsJsonObject("icon"));
        if (md.has("contributors")) m.authors.addAll(md.getAsJsonObject("contributors").keySet());
        JsonObject mc = root.has("minecraft") ? root.getAsJsonObject("minecraft") : null;
        if (mc != null && mc.has("environment")) {
            String env = mc.get("environment").getAsString();
            m.environment = env.equals("dedicated_server") ? "server" : env;
        }
        JsonElement mix = root.get("mixin");
        if (mix != null) {
            if (mix.isJsonPrimitive()) m.mixins.add(new Mixin(mix.getAsString(), "*"));
            else for (JsonElement e : mix.getAsJsonArray()) m.mixins.add(new Mixin(e.getAsString(), "*"));
        }
        if (q.has("jars")) for (JsonElement e : q.getAsJsonArray("jars")) m.nestedJars.add(e.isJsonPrimitive() ? e.getAsString() : e.getAsJsonObject().get("file").getAsString());
        if (q.has("depends")) for (JsonElement e : q.getAsJsonArray("depends")) {
            if (e.isJsonPrimitive()) m.depends.put(e.getAsString(), "*");
            else if (e.getAsJsonObject().has("id") && !(e.getAsJsonObject().has("optional") && e.getAsJsonObject().get("optional").getAsBoolean()))
                m.depends.put(e.getAsJsonObject().get("id").getAsString(), "*");
        }
        if (q.has("provides")) for (JsonElement e : q.getAsJsonArray("provides")) m.provides.add(e.isJsonPrimitive() ? e.getAsString() : e.getAsJsonObject().get("id").getAsString());
        JsonElement aw = root.get("access_widener");
        if (aw != null) m.accessWidener = aw.isJsonPrimitive() ? aw.getAsString() : aw.getAsJsonArray().get(0).getAsString();
        return m;
    }

    private static String flat(JsonElement e) {
        if (e.isJsonPrimitive()) return e.getAsString();
        if (e.isJsonArray()) {
            List<String> s = new ArrayList<>();
            for (JsonElement x : (JsonArray) e) s.add(flat(x));
            return String.join(" or ", s);
        }
        if (e.isJsonObject() && e.getAsJsonObject().has("id")) return e.getAsJsonObject().get("id").getAsString();
        return e.toString();
    }

    private static String largestIcon(JsonObject icons) {
        String best = null; int bestSize = -1;
        for (var e : icons.entrySet()) {
            int s; try { s = Integer.parseInt(e.getKey()); } catch (NumberFormatException ex) { s = 0; }
            if (s > bestSize) { bestSize = s; best = e.getValue().getAsString(); }
        }
        return best;
    }
}
