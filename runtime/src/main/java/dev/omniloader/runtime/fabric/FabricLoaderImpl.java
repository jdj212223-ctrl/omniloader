package dev.omniloader.runtime.fabric;

import dev.omniloader.runtime.Platform;
import dev.omniloader.runtime.RefChecker;

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
    public record MixinOwner(ModContainerImpl mod, String plugin) {}

    private final Map<String, MixinOwner> mixinOwners = new HashMap<>();
    /** Adapted NeoForge mods whose start-up Omniloader holds back until they pass the check. */
    public final Map<ModContainerImpl, JsonObject> heldBack = new LinkedHashMap<>();

    /** Which mod a mixin package belongs to, and the mod's own mixin plugin (if any). */
    public MixinOwner mixinOwner(String mixinPackage) {
        setup();
        String p = mixinPackage.endsWith(".") ? mixinPackage.substring(0, mixinPackage.length() - 1) : mixinPackage;
        return mixinOwners.get(p);
    }

    private final Map<String, ModContainerImpl> mods = new LinkedHashMap<>();
    private final Map<String, List<Entry>> entrypoints = new HashMap<>();
    private final Map<String, Object> instances = new HashMap<>();
    private final ObjectShareImpl objectShare = new ObjectShareImpl();
    private final MappingResolverImpl mappingResolver = new MappingResolverImpl();
    private boolean setUp;

    private FabricLoaderImpl() {}

    /** Gathers every mod in the game. Works from the moment mixins load (uses NeoForge's early mod list). */
    public synchronized void setup() {
        if (setUp) return;
        setUp = true;
        Map<String, ModContainerImpl> byNeoId = new HashMap<>();
        Map<ModContainerImpl, String> toCheck = new LinkedHashMap<>();
        Map<ModContainerImpl, String> offRefs = new LinkedHashMap<>();
        Map<ModContainerImpl, JsonObject> descriptors = new HashMap<>();
        Map<ModContainerImpl, Boolean> quiltMods = new HashMap<>();
        // 1) Fabric and Quilt mods that Omniloader converted.
        for (IModFileInfo info : Platform.modFiles()) {
            IModFile file = info.getFile();
            try {
                String marker = Platform.readText(file, "META-INF/omniloader/mod.json");
                if (marker == null) continue;
                JsonObject om = JsonParser.parseString(marker).getAsJsonObject();
                String loader = om.get("loader").getAsString();
                boolean quilt = loader.equals("quilt"), neo = loader.equals("neoforge") || loader.equals("forge");
                JsonObject json;
                ModMetadataImpl meta;
                if (neo) {
                    // A NeoForge mod from another Minecraft version / newer Java that Omniloader adapted.
                    IModInfo mi = file.getModInfos().get(0);
                    meta = ModMetadataImpl.simple("neoforge", mi.getModId(), mi.getDisplayName(), mi.getVersion().toString());
                    json = om;
                } else {
                    json = JsonParser.parseString(Platform.readText(file, quilt ? "quilt.mod.json" : "fabric.mod.json")).getAsJsonObject();
                    meta = quilt ? ModMetadataImpl.fromQuilt(json) : ModMetadataImpl.fromFabric(json);
                }
                String neoId = om.get("neoId").getAsString();
                ModContainerImpl c = new ModContainerImpl(meta, Platform.roots(file), file.getFilePath(), !neo,
                        om.get("active").getAsBoolean(), neoId);
                c.crossVersion = om.has("crossVersion") && om.get("crossVersion").getAsBoolean();
                c.builtFor = om.has("builtFor") ? om.get("builtFor").getAsString() : null;
                c.builtForJava = om.has("builtForJava") ? om.get("builtForJava").getAsInt() : 0;
                if (!c.active && om.has("reason")) c.offReason = om.get("reason").getAsString();
                c.forge = loader.equals("forge");
                if (neo) heldBack.put(c, om);
                RefChecker.addProvided(Platform.readText(file, "META-INF/omniloader/adds.txt"));
                String mixins = Platform.readText(file, "META-INF/omniloader/mixins.txt");
                if (mixins != null) for (String l : mixins.split("\n")) {
                    String[] p = l.split("\t");
                    if (p.length == 2) mixinOwners.put(p[0], new MixinOwner(c, p[1].equals("-") ? null : p[1]));
                }
                if (c.active) toCheck.put(c, Platform.readText(file, "META-INF/omniloader/refs.txt"));
                else offRefs.put(c, Platform.readText(file, "META-INF/omniloader/refs.txt"));
                descriptors.put(c, json);
                quiltMods.put(c, quilt);
                mods.put(meta.getId(), c);
                byNeoId.put(neoId, c);
                for (String p : meta.getProvides()) mods.putIfAbsent(p, c);

            } catch (Exception e) {
                LOG.error("[Omniloader] Could not read mod info from {}", file.getFileName(), e);
            }
        }
        // Before starting anything: does every piece of Minecraft each mod uses actually exist here?
        StringBuilder report = new StringBuilder("Omniloader report: mods that were switched off, and every piece of the game each one is missing.\n");
        String provided = readResource("/META-INF/omniloader/provided-members.txt");
        RefChecker.addProvided(provided);
        String apiAdds = System.getProperty("omniloader.apiAdds");
        if (apiAdds != null) try { RefChecker.addProvided(java.nio.file.Files.readString(java.nio.file.Path.of(apiAdds))); } catch (Exception ignored) {}
        LOG.info("[Omniloader] Checking {} Fabric/Quilt mod(s) against this game ({} added members known)", toCheck.size(), RefChecker.ADDED.size());
        for (var e : toCheck.entrySet()) {
            ModContainerImpl c = e.getKey();
            List<String> missing = e.getValue() == null ? List.of() : RefChecker.missing(e.getValue());
            if (!missing.isEmpty()) {
                report.append("\n== ").append(c.getMetadata().getName()).append(" (").append(c.getMetadata().getId()).append(")")
                        .append(c.crossVersion ? ", made for Minecraft " + c.builtFor : "").append(c.builtForJava > 0 ? ", Java " + c.builtForJava : "")
                        .append("\n   missing ").append(missing.size()).append(":\n");
                for (String m : missing) report.append("   - ").append(m).append('\n');
                c.active = false;
                String list = String.join(", ", missing.subList(0, Math.min(5, missing.size()))) + (missing.size() > 5 ? " (+" + (missing.size() - 5) + " more)" : "");
                c.offReason = c.forge
                        ? "it's a Forge mod that uses parts of Forge Omniloader can't translate yet: " + list
                        : c.crossVersion
                        ? "it was made for Minecraft " + c.builtFor + " and uses things Minecraft " + Platform.MC + " doesn't have: " + list
                        : c.builtForJava > 0 ? "it was made for Java " + c.builtForJava + " and uses things Java " + Runtime.version().feature() + " doesn't have: " + list
                        : "it uses things this game doesn't have (most likely parts of Fabric API Omniloader doesn't have yet): " + list;
                LOG.warn("[Omniloader] Switching off {}: {}", c.getMetadata().getName(), c.offReason);
            } else if (c.crossVersion || c.builtForJava > 0) {
                LOG.info("[Omniloader] {} was made for {}; everything it uses exists here - starting it", c.getMetadata().getName(),
                        c.crossVersion ? "Minecraft " + c.builtFor : "Java " + c.builtForJava);
            }
            if (c.active && c.foreign) {
                JsonObject json = descriptors.get(c);
                readEntrypoints(c, quiltMods.get(c) ? json.getAsJsonObject("quilt_loader").get("entrypoints") : json.get("entrypoints"));
            }
        }

        // Mods already switched off while being converted (whole classes missing, Fabric API missing, wrong side...).
        for (ModContainerImpl c : byNeoId.values())
            if (!c.active && c.offReason != null && !toCheck.containsKey(c)) {
                report.append("\n== ").append(c.getMetadata().getName()).append(" (").append(c.getMetadata().getId()).append(")\n   ").append(c.offReason).append('\n');
                String refs = offRefs.get(c);
                if (refs != null && !refs.isBlank()) {
                    // Full picture: everything it would need that this game (with Omniloader) doesn't have.
                    List<String> all = RefChecker.missing(refs);
                    report.append("   everything it would need that's missing here (").append(all.size()).append("):\n");
                    for (String m : all) report.append("   - ").append(m).append('\n');
                }
            }
        try {
            java.nio.file.Path rp = getGameDir().resolve(".omniloader").resolve("report.txt");
            java.nio.file.Files.createDirectories(rp.getParent());
            java.nio.file.Files.writeString(rp, report);
        } catch (Exception ignored) {}

        // 2) Everything else NeoForge loaded, so isModLoaded("sodium") etc. answer truthfully.
        for (IModInfo info : Platform.mods()) {
            if (byNeoId.containsKey(info.getModId())) continue;
            ModMetadataImpl meta = ModMetadataImpl.simple("builtin".equals(info.getModId()) ? "builtin" : "neoforge",
                    info.getModId(), info.getDisplayName(), info.getVersion().toString());
            meta.description = info.getDescription();
            List<Path> roots = Platform.roots(info.getOwningFile().getFile());
            ModContainerImpl c = new ModContainerImpl(meta, roots, info.getOwningFile().getFile().getFilePath(), false, true, info.getModId());
            mods.putIfAbsent(info.getModId(), c);
        }
        // 3) The pieces Fabric mods expect to always be there.
        ModContainerImpl mc = mods.get("minecraft");
        if (mc != null) mc.meta().type = "builtin";
        addBuiltin("fabricloader", "Fabric Loader (Omniloader)", FABRIC_LOADER_VERSION);
        addBuiltin("java", "Java " + Runtime.version().feature(), String.valueOf(Runtime.version().feature()));
        // Parent/child links for mods that were bundled inside other mods.
        for (IModFileInfo info : Platform.modFiles()) {
            IModFile f = info.getFile();
            IModFile parent = f.getDiscoveryAttributes().parent();
            if (parent == null || f.getModInfos().isEmpty() || parent.getModInfos().isEmpty()) continue;
            ModContainerImpl child = byNeoId.get(f.getModInfos().get(0).getModId());
            ModContainerImpl par = byNeoId.get(parent.getModInfos().get(0).getModId());
            if (child != null && par != null) { child.parent = par; par.children.add(child); }
        }
    }

    private static String readResource(String path) {
        // Our own jar first (it's a module, so ask it directly), then the class loader.
        try (var in = FabricLoaderImpl.class.getModule().getResourceAsStream(path.substring(1))) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
        try (var in = FabricLoaderImpl.class.getClassLoader().getResourceAsStream(path.substring(1))) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
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

    /** Adds a mod switched off late (while starting) to the report file. */
    public void noteSwitchedOff(ModContainerImpl c) {
        try {
            java.nio.file.Path rp = getGameDir().resolve(".omniloader").resolve("report.txt");
            java.nio.file.Files.writeString(rp, "\n== " + c.getMetadata().getName() + " (" + c.getMetadata().getId() + ")\n   " + c.offReason + "\n",
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }

    /** Switches a mod off after startup checks (e.g. its mixin plugin failed). Its startup code won't run. */
    public synchronized void switchOff(ModContainerImpl c, String reason) {
        if (c == null || !c.active) return;
        c.active = false;
        c.offReason = reason;
        LOG.warn("[Omniloader] Switching off {}: {}", c.getMetadata().getName(), reason);
        entrypoints.values().forEach(l -> l.removeIf(e -> e.mod() == c));
    }

    public List<Entry> rawEntrypoints(String key) { setup(); return entrypoints.getOrDefault(key, List.of()); }

    public Collection<ModContainerImpl> containers() { setup(); return Collections.unmodifiableCollection(mods.values()); }

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

    @Override public boolean isDevelopmentEnvironment() { return !Platform.isProduction(); }
    @Override public EnvType getEnvironmentType() { return Platform.isClient() ? EnvType.CLIENT : EnvType.SERVER; }
    @Override public Object getGameInstance() { return null; }
    @Override public Path getGameDir() { return FMLPaths.GAMEDIR.get(); }
    @Override public File getGameDirectory() { return getGameDir().toFile(); }
    @Override public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }
    @Override public File getConfigDirectory() { return getConfigDir().toFile(); }
    @Override public String[] getLaunchArguments(boolean sanitize) { return new String[0]; }
}
