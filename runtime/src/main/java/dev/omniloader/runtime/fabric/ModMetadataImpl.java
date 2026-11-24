package dev.omniloader.runtime.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Fabric-style description of any mod in the game (Fabric, Quilt, NeoForge, or built-in). */
public final class ModMetadataImpl implements ModMetadata {
    public String type = "fabric";
    public String id;
    public final List<String> provides = new ArrayList<>();
    public Version version;
    public ModEnvironment environment = ModEnvironment.UNIVERSAL;
    public final List<ModDependency> dependencies = new ArrayList<>();
    public String name;
    public String description = "";
    public final List<Person> authors = new ArrayList<>();
    public final List<Person> contributors = new ArrayList<>();
    public ContactInformation contact = ContactInformation.EMPTY;
    public final List<String> license = new ArrayList<>();
    public final TreeMap<Integer, String> icons = new TreeMap<>();
    public final Map<String, CustomValue> custom = new LinkedHashMap<>();

    public static ModMetadataImpl simple(String type, String id, String name, String version) {
        ModMetadataImpl m = new ModMetadataImpl();
        m.type = type; m.id = id; m.name = name; m.version = VersionImpl.parseOrPlain(version);
        return m;
    }

    record PersonImpl(String getName, ContactInformation getContact) implements Person {}

    record ContactImpl(Map<String, String> asMap) implements ContactInformation {
        public Optional<String> get(String key) { return Optional.ofNullable(asMap.get(key)); }
    }

    record DependencyImpl(Kind getKind, String getModId, List<String> ranges) implements ModDependency {
        public boolean matches(Version version) { return true; } // version ranges are not enforced yet
    }

    private static ContactInformation contact(JsonElement e) {
        Map<String, String> m = new LinkedHashMap<>();
        if (e != null && e.isJsonObject()) for (var x : e.getAsJsonObject().entrySet()) m.put(x.getKey(), x.getValue().getAsString());
        return new ContactImpl(m);
    }

    private static Person person(JsonElement e) {
        if (e.isJsonPrimitive()) return new PersonImpl(e.getAsString(), ContactInformation.EMPTY);
        JsonObject o = e.getAsJsonObject();
        return new PersonImpl(o.get("name").getAsString(), contact(o.get("contact")));
    }

    public static ModMetadataImpl fromFabric(JsonObject o) {
        ModMetadataImpl m = new ModMetadataImpl();
        m.type = "fabric";
        m.id = o.get("id").getAsString();
        m.version = VersionImpl.parseOrPlain(o.has("version") ? o.get("version").getAsString() : "0.0.0");
        m.name = o.has("name") ? o.get("name").getAsString() : m.id;
        if (o.has("description")) m.description = o.get("description").getAsString();
        if (o.has("provides")) o.getAsJsonArray("provides").forEach(x -> m.provides.add(x.getAsString()));
        if (o.has("environment")) m.environment = switch (o.get("environment").getAsString()) {
            case "client" -> ModEnvironment.CLIENT; case "server" -> ModEnvironment.SERVER; default -> ModEnvironment.UNIVERSAL; };
        if (o.has("authors")) o.getAsJsonArray("authors").forEach(x -> m.authors.add(person(x)));
        if (o.has("contributors")) o.getAsJsonArray("contributors").forEach(x -> m.contributors.add(person(x)));
        if (o.has("contact")) m.contact = contact(o.get("contact"));
        if (o.has("license")) {
            JsonElement l = o.get("license");
            if (l.isJsonArray()) l.getAsJsonArray().forEach(x -> m.license.add(x.getAsString())); else m.license.add(l.getAsString());
        }
        if (o.has("icon")) {
            JsonElement i = o.get("icon");
            if (i.isJsonPrimitive()) m.icons.put(0, i.getAsString());
            else for (var x : i.getAsJsonObject().entrySet()) {
                try { m.icons.put(Integer.parseInt(x.getKey()), x.getValue().getAsString()); } catch (NumberFormatException ignored) {}
            }
        }
        for (ModDependency.Kind k : ModDependency.Kind.values()) {
            if (!o.has(k.getKey())) continue;
            for (var x : o.getAsJsonObject(k.getKey()).entrySet()) {
                List<String> ranges = new ArrayList<>();
                if (x.getValue().isJsonArray()) x.getValue().getAsJsonArray().forEach(r -> ranges.add(r.getAsString()));
                else ranges.add(x.getValue().getAsString());
                m.dependencies.add(new DependencyImpl(k, x.getKey(), ranges));
            }
        }
        if (o.has("custom")) for (var x : o.getAsJsonObject("custom").entrySet()) m.custom.put(x.getKey(), CustomValueImpl.of(x.getValue()));
        return m;
    }

    public static ModMetadataImpl fromQuilt(JsonObject root) {
        JsonObject q = root.getAsJsonObject("quilt_loader");
        ModMetadataImpl m = new ModMetadataImpl();
        m.type = "quilt";
        m.id = q.get("id").getAsString();
        m.version = VersionImpl.parseOrPlain(q.has("version") ? q.get("version").getAsString() : "0.0.0");
        JsonObject md = q.has("metadata") ? q.getAsJsonObject("metadata") : new JsonObject();
        m.name = md.has("name") ? md.get("name").getAsString() : m.id;
        if (md.has("description")) m.description = md.get("description").getAsString();
        if (md.has("contributors")) for (var x : md.getAsJsonObject("contributors").entrySet()) m.authors.add(new PersonImpl(x.getKey(), ContactInformation.EMPTY));
        if (md.has("contact")) m.contact = contact(md.get("contact"));
        if (md.has("icon") && md.get("icon").isJsonPrimitive()) m.icons.put(0, md.get("icon").getAsString());
        if (q.has("provides")) q.getAsJsonArray("provides").forEach(x -> m.provides.add(x.isJsonPrimitive() ? x.getAsString() : x.getAsJsonObject().get("id").getAsString()));
        for (var x : root.entrySet()) if (!x.getKey().equals("quilt_loader") && !x.getKey().equals("schema_version")) m.custom.put(x.getKey(), CustomValueImpl.of(x.getValue()));
        return m;
    }

    public String getType() { return type; }
    public String getId() { return id; }
    public Collection<String> getProvides() { return provides; }
    public Version getVersion() { return version; }
    public ModEnvironment getEnvironment() { return environment; }
    public Collection<ModDependency> getDependencies() { return dependencies; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Collection<Person> getAuthors() { return authors; }
    public Collection<Person> getContributors() { return contributors; }
    public ContactInformation getContact() { return contact; }
    public Collection<String> getLicense() { return license; }
    public Optional<String> getIconPath(int size) {
        if (icons.isEmpty()) return Optional.empty();
        var e = icons.ceilingEntry(size);
        return Optional.of((e != null ? e : icons.lastEntry()).getValue());
    }
    public boolean containsCustomValue(String key) { return custom.containsKey(key); }
    public CustomValue getCustomValue(String key) { return custom.get(key); }
    public Map<String, CustomValue> getCustomValues() { return custom; }
}
