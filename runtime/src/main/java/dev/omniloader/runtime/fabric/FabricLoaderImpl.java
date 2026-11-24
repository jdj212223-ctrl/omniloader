package dev.omniloader.runtime.fabric;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.EntrypointException;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.ObjectShare;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Omniloader's implementation of the Fabric Loader API, backed by NeoForge's mod list. */
public final class FabricLoaderImpl implements FabricLoader {
    public static final FabricLoaderImpl INSTANCE = new FabricLoaderImpl();
    /** The Fabric Loader version we claim to match, for mods that check it. */
    public static final String FABRIC_LOADER_VERSION = "0.16.9";
    private static final Logger LOG = LoggerFactory.getLogger("Omniloader");

    public record Entry(ModContainerImpl mod, String value, String adapter) {}

    private final Map<String, ModContainerImpl> mods = new LinkedHashMap<>();
    private final Map<String, List<Entry>> entrypoints = new HashMap<>();
    private final Map<String, Object> instances = new HashMap<>();
    private final ObjectShareImpl objectShare = new ObjectShareImpl();
    private final MappingResolverImpl mappingResolver = new MappingResolverImpl();
    private boolean setUp;

    private FabricLoaderImpl() {}

    /** Gathers every mod in the game. Safe to call more than once. */
    public synchronized void setup() {
        if (setUp) return;
        setUp = true;
        Map<String, ModContainerImpl> byNeoId = new HashMap<>();
        // 1) Fabric and Quilt mods that Omniloader converted.
        for (IModFileInfo info : ModList.get().getModFiles()) {
            IModFile file = info.getFile();
            Path marker = file.findResource("META-INF/omniloader/mod.json");
            if (!Files.isRegularFile(marker)) continue;
            try {
                JsonObject om = JsonParser.parseString(Files.readString(marker)).getAsJsonObject();
                boolean quilt = om.get("loader").getAsString().equals("quilt");
                Path descriptor = file.findResource(quilt ? "quilt.mod.json" : "fabric.mod.json");
                JsonObject json = JsonParser.parseString(Files.readString(descriptor, StandardCharsets.UTF_8)).getAsJsonObject();
                ModMetadataImpl meta = quilt ? ModMetadataImpl.fromQuilt(json) : ModMetadataImpl.fromFabric(json);
                String neoId = om.get("neoId").getAsString();
                ModContainerImpl c = new ModContainerImpl(meta, List.of(file.getSecureJar().getRootPath()), file.getFilePath(), true,
                        om.get("active").getAsBoolean(), neoId);
                mods.put(meta.getId(), c);
                byNeoId.put(neoId, c);
                for (String p : meta.getProvides()) mods.putIfAbsent(p, c);
                if (c.active) readEntrypoints(c, quilt ? json.getAsJsonObject("quilt_loader").get("entrypoints") : json.get("entrypoints"));
            } catch (Exception e) {
                LOG.error("[Omniloader] Could not read mod info from {}", file.getFileName(), e);
            }
        }
        // 2) Everything else NeoForge loaded, so isModLoaded("sodium") etc. answer truthfully.
        for (IModInfo info : ModList.get().getMods()) {
            if (byNeoId.containsKey(info.getModId())) continue;
            ModMetadataImpl meta = ModMetadataImpl.simple("builtin".equals(info.getModId()) ? "builtin" : "neoforge",
                    info.getModId(), info.getDisplayName(), info.getVersion().toString());
            meta.description = info.getDescription();
            List<Path> roots = List.of(info.getOwningFile().getFile().getSecureJar().getRootPath());
            ModContainerImpl c = new ModContainerImpl(meta, roots, info.getOwningFile().getFile().getFilePath(), false, true, info.getModId());
            mods.putIfAbsent(info.getModId(), c);
        }
        // 3) The pieces Fabric mods expect to always be there.
        ModContainerImpl mc = mods.get("minecraft");
        if (mc != null) mc.meta().type = "builtin";
        addBuiltin("fabricloader", "Fabric Loader (Omniloader)", FABRIC_LOADER_VERSION);
        addBuiltin("java", "Java " + Runtime.version().feature(), String.valueOf(Runtime.version().feature()));
        // Parent/child links for mods that were bundled inside other mods.
        for (IModFileInfo info : ModList.get().getModFiles()) {
            IModFile f = info.getFile();
            IModFile parent = f.getDiscoveryAttributes().parent();
            if (parent == null || f.getModInfos().isEmpty() || parent.getModInfos().isEmpty()) continue;
            ModContainerImpl child = byNeoId.get(f.getModInfos().get(0).getModId());
            ModContainerImpl par = byNeoId.get(parent.getModInfos().get(0).getModId());
            if (child != null && par != null) { child.parent = par; par.children.add(child); }
        }
    }

    private void addBuiltin(String id, String name, String version) {
        if (mods.containsKey(id)) return;
        mods.put(id, new ModContainerImpl(ModMetadataImpl.simple("builtin", id, name, version), List.of(), null, false, true, id));
    }

    private void readEntrypoints(ModContainerImpl c, JsonElement el) {
        if (el == null || !el.isJsonObject()) return;
        for (var e : el.getAsJsonObject().entrySet()) {
            List<JsonElement> list = new ArrayList<>();
            if (e.getValue().isJsonArray()) e.getValue().getAsJsonArray().forEach(list::add); else list.add(e.getValue());
            for (JsonElement v : list) {
                String value, adapter = "default";
                if (v.isJsonPrimitive()) value = v.getAsString();
                else {
                    value = v.getAsJsonObject().get("value").getAsString();
                    if (v.getAsJsonObject().has("adapter")) adapter = v.getAsJsonObject().get("adapter").getAsString();
                }
                entrypoints.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(new Entry(c, value, adapter));
            }
        }
    }

    public List<Entry> rawEntrypoints(String key) { return entrypoints.getOrDefault(key, List.of()); }

    public Collection<ModContainerImpl> containers() { return Collections.unmodifiableCollection(mods.values()); }

    /** Creates (or reuses) the object an entrypoint string points at. */
    public synchronized Object instantiate(Entry e, Class<?> type) throws Exception {
        if (!e.adapter().equals("default")) throw new IllegalStateException("language adapter '" + e.adapter() + "' is not supported yet");
        String value = e.value();
        ClassLoader cl = FabricLoaderImpl.class.getClassLoader();
        int sep = value.indexOf("::");
        if (sep < 0) {
            Object cached = instances.get(value);
            if (cached != null && type.isInstance(cached)) return cached;
            Class<?> c = Class.forName(value, true, cl);
            Object o = c.getDeclaredConstructor().newInstance();
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
        Class<?> itf = type.isInterface() ? type : null;
        if (itf == null) throw new IllegalStateException("cannot wrap method " + value + " as " + type);
        return Proxy.newProxyInstance(cl, new Class<?>[]{itf}, (proxy, m, args) -> {
            if (m.getDeclaringClass() == Object.class) return m.invoke(this, args);
            return fm.invoke(owner, args);
        });
    }

    @Override
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (EntrypointContainer<T> c : getEntrypointContainers(key, type)) out.add(c.getEntrypoint());
        return out;
    }

    @Override
    public <T> List<EntrypointContainer<T>> getEntrypointContainers(String key, Class<T> type) {
        List<EntrypointContainer<T>> out = new ArrayList<>();
        for (Entry e : rawEntrypoints(key)) {
            T obj;
            try {
                obj = type.cast(instantiate(e, type));
            } catch (Throwable t) {
                throw new EntrypointException(key, e.mod().getMetadata().getId(), t);
            }
            out.add(new EntrypointContainer<>() {
                public T getEntrypoint() { return obj; }
                public ModContainer getProvider() { return e.mod(); }
                public String getDefinition() { return e.value(); }
            });
        }
        return out;
    }

    @Override
    public <T> void invokeEntrypoints(String key, Class<T> type, Consumer<? super T> invoker) {
        for (EntrypointContainer<T> c : getEntrypointContainers(key, type)) {
            try { invoker.accept(c.getEntrypoint()); }
            catch (Throwable t) { throw new EntrypointException(key, c.getProvider().getMetadata().getId(), t); }
        }
    }

    @Override public ObjectShare getObjectShare() { return objectShare; }
    @Override public MappingResolver getMappingResolver() { return mappingResolver; }
    @Override public Optional<ModContainer> getModContainer(String id) { setup(); return Optional.ofNullable(lookup(id)); }
    @Override public Collection<ModContainer> getAllMods() { setup(); return new ArrayList<>(new java.util.LinkedHashSet<>(mods.values())); }
    @Override public boolean isModLoaded(String id) { setup(); return lookup(id) != null; }

    private ModContainerImpl lookup(String id) {
        ModContainerImpl c = mods.get(id);
        if (c == null) c = mods.get(id.toLowerCase().replaceAll("[^a-z0-9_]", "_"));
        return c;
    }

    @Override public boolean isDevelopmentEnvironment() { return !FMLLoader.isProduction(); }
    @Override public EnvType getEnvironmentType() { return FMLEnvironment.dist.isClient() ? EnvType.CLIENT : EnvType.SERVER; }
    @Override public Object getGameInstance() { return null; }
    @Override public Path getGameDir() { return FMLPaths.GAMEDIR.get(); }
    @Override public File getGameDirectory() { return getGameDir().toFile(); }
    @Override public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }
    @Override public File getConfigDirectory() { return getConfigDir().toFile(); }
    @Override public String[] getLaunchArguments(boolean sanitize) { return new String[0]; }
}
