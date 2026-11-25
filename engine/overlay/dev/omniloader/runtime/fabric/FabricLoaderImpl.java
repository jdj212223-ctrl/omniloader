package dev.omniloader.runtime.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.omniloader.engine.Engine;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.EntrypointException;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** The Fabric Loader API on Omniloader's own engine (no NeoForge underneath). */
public final class FabricLoaderImpl implements FabricLoader {
    public static final FabricLoaderImpl INSTANCE = new FabricLoaderImpl();
    public static final String FABRIC_LOADER_VERSION = "0.16.9";

    public record Entry(ModContainerImpl mod, String value, String adapter) {}
    public record MixinOwner(ModContainerImpl mod, String plugin) {}

    private final Map<String, ModContainerImpl> mods = new LinkedHashMap<>();
    private final Map<String, List<Entry>> entrypoints = new HashMap<>();
    private final Map<String, Object> instances = new HashMap<>();
    private final Map<String, MixinOwner> mixinOwners = new HashMap<>();
    private final ObjectShareImpl objectShare = new ObjectShareImpl();
    private final MappingResolverImpl mappingResolver = new MappingResolverImpl();

    private FabricLoaderImpl() {}

    /** One converted Fabric/Quilt mod jar. */
    public record Loaded(Path jar, boolean active, String reason) {}

    public synchronized void setup(List<Loaded> loaded) throws Exception {
        for (Loaded l : loaded) {
            try (ZipFile z = new ZipFile(l.jar().toFile())) {
                var om = JsonParser.parseString(new String(z.getInputStream(z.getEntry("META-INF/omniloader/mod.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                boolean quilt = om.get("loader").getAsString().equals("quilt");
                var desc = z.getEntry(quilt ? "quilt.mod.json" : "fabric.mod.json");
                JsonObject json = JsonParser.parseString(new String(z.getInputStream(desc).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                ModMetadataImpl meta = quilt ? ModMetadataImpl.fromQuilt(json) : ModMetadataImpl.fromFabric(json);
                Path root = FileSystems.newFileSystem(l.jar()).getPath("/");
                ModContainerImpl c = new ModContainerImpl(meta, List.of(root), l.jar(), true, l.active(), om.get("neoId").getAsString());
                c.offReason = l.reason();
                mods.put(meta.getId(), c);
                for (String p : meta.getProvides()) mods.putIfAbsent(p, c);
                var mixins = z.getEntry("META-INF/omniloader/mixins.txt");
                if (mixins != null) for (String line : new String(z.getInputStream(mixins).readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                    String[] p = line.split("\t");
                    if (p.length == 2) mixinOwners.put(p[0], new MixinOwner(c, p[1].equals("-") ? null : p[1]));
                }
                if (c.active) readEntrypoints(c, quilt ? json.getAsJsonObject("quilt_loader").get("entrypoints") : json.get("entrypoints"));
            }
        }
        builtin("minecraft", "Minecraft", dev.omniloader.loader.Platform.MC_FULL);
        builtin("java", "Java " + Runtime.version().feature(), String.valueOf(Runtime.version().feature()));
        builtin("fabricloader", "Fabric Loader (Omniloader)", FABRIC_LOADER_VERSION);
        builtin("omniloader", "Omniloader", Engine.VERSION);
    }

    private void builtin(String id, String name, String version) {
        mods.putIfAbsent(id, new ModContainerImpl(ModMetadataImpl.simple("builtin", id, name, version), List.of(), null, false, true, id));
    }

    private void readEntrypoints(ModContainerImpl c, JsonElement el) {
        if (el == null || !el.isJsonObject()) return;
        for (var e : el.getAsJsonObject().entrySet()) {
            List<JsonElement> list = new ArrayList<>();
            if (e.getValue().isJsonArray()) e.getValue().getAsJsonArray().forEach(list::add); else list.add(e.getValue());
            for (JsonElement v : list) {
                String value, adapter = "default";
                if (v.isJsonPrimitive()) value = v.getAsString();
                else { value = v.getAsJsonObject().get("value").getAsString(); if (v.getAsJsonObject().has("adapter")) adapter = v.getAsJsonObject().get("adapter").getAsString(); }
                entrypoints.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(new Entry(c, value, adapter));
            }
        }
    }

    public MixinOwner mixinOwner(String mixinPackage) {
        String p = mixinPackage.endsWith(".") ? mixinPackage.substring(0, mixinPackage.length() - 1) : mixinPackage;
        return mixinOwners.get(p);
    }

    public synchronized void switchOff(ModContainerImpl c, String reason) {
        if (c == null || !c.active) return;
        c.active = false;
        c.offReason = reason;
        Engine.log("Switching off " + c.getMetadata().getName() + ": " + reason);
        entrypoints.values().forEach(l -> l.removeIf(e -> e.mod() == c));
    }

    public List<Entry> rawEntrypoints(String key) { return entrypoints.getOrDefault(key, List.of()); }
    public Collection<ModContainerImpl> containers() { return mods.values(); }

    public synchronized Object instantiate(Entry e, Class<?> type) throws Exception {
        if (!e.adapter().equals("default")) throw new IllegalStateException("language adapter '" + e.adapter() + "' is not supported yet");
        String value = e.value();
        ClassLoader cl = Engine.loader;
        int sep = value.indexOf("::");
        if (sep < 0) {
            Object cached = instances.get(value);
            if (cached != null && type.isInstance(cached)) return cached;
            Object o = Class.forName(value, true, cl).getDeclaredConstructor().newInstance();
            instances.put(value, o);
            return o;
        }
        Class<?> c = Class.forName(value.substring(0, sep), true, cl);
        String member = value.substring(sep + 2);
        try {
            Field f = c.getDeclaredField(member);
            if (Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); return f.get(null); }
        } catch (NoSuchFieldException ignored) {}
        Method target = null;
        for (Method m : c.getDeclaredMethods()) if (m.getName().equals(member)) { target = m; break; }
        if (target == null) throw new NoSuchMethodException(value);
        Method fm = target;
        fm.setAccessible(true);
        Object owner = Modifier.isStatic(fm.getModifiers()) ? null : c.getDeclaredConstructor().newInstance();
        if (!type.isInterface()) throw new IllegalStateException("cannot wrap method " + value + " as " + type);
        return Proxy.newProxyInstance(cl, new Class<?>[]{type}, (proxy, m, args) -> m.getDeclaringClass() == Object.class ? m.invoke(this, args) : fm.invoke(owner, args));
    }

    @Override public <T> List<T> getEntrypoints(String key, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (EntrypointContainer<T> c : getEntrypointContainers(key, type)) out.add(c.getEntrypoint());
        return out;
    }

    @Override public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        List<EntrypointContainer<T>> out = new ArrayList<>();
        for (Entry e : rawEntrypoints(key)) {
            T obj;
            try { obj = type.cast(instantiate(e, type)); }
            catch (Throwable t) { throw new EntrypointException(key, e.mod().getMetadata().getId(), t); }
            out.add(new EntrypointContainer<>() {
                public T getEntrypoint() { return obj; }
                public ModContainer getProvider() { return e.mod(); }
                public String getDefinition() { return e.value(); }
            });
        }
        return out;
    }

    @Override public <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker) {
        for (EntrypointContainer<T> c : getEntrypointContainers(key, type)) invoker.accept(c.getEntrypoint());
    }

    @Override public ObjectShare getObjectShare() { return objectShare; }
    @Override public MappingResolver getMappingResolver() { return mappingResolver; }
    @Override public Optional<ModContainer> getModContainer(String id) { return Optional.ofNullable(lookup(id)); }
    @Override public Collection<ModContainer> getAllMods() { return new ArrayList<>(new LinkedHashSet<>(mods.values())); }
    @Override public boolean isModLoaded(String id) { return lookup(id) != null; }
    private ModContainerImpl lookup(String id) { ModContainerImpl c = mods.get(id); return c != null ? c : mods.get(id.toLowerCase().replaceAll("[^a-z0-9_]", "_")); }
    @Override public boolean isDevelopmentEnvironment() { return false; }
    @Override public EnvType getEnvironmentType() { return Engine.client ? EnvType.CLIENT : EnvType.SERVER; }
    @Override public Object getGameInstance() { return null; }
    @Override public Path getGameDir() { return Engine.gameDir; }
    @Override public File getGameDirectory() { return getGameDir().toFile(); }
    @Override public Path getConfigDir() { return Engine.gameDir.resolve("config"); }
    @Override public File getConfigDirectory() { return getConfigDir().toFile(); }
    @Override public String[] getLaunchArguments(boolean sanitize) { return Engine.args; }
}
