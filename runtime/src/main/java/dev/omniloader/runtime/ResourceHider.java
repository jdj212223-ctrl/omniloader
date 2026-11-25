package dev.omniloader.runtime;

import dev.omniloader.runtime.fabric.FabricLoaderImpl;
import dev.omniloader.runtime.fabric.ModContainerImpl;
import net.neoforged.neoforge.resource.ResourcePackLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A switched-off mod's data (recipes, world generation, spawn rules...) and textures would still load and
 * point at blocks and mobs that were never added, which breaks the world. NeoForge builds its list of mod
 * resource packs once; we build it first and leave switched-off mods out.
 */
public final class ResourceHider {
    private static final Logger LOG = LoggerFactory.getLogger("Omniloader");

    private ResourceHider() {}

    public static void hideSwitchedOffMods() {
        Set<Path> off = FabricLoaderImpl.INSTANCE.containers().stream()
                .filter(c -> !c.active && c.getOrigin().getPaths().size() == 1)
                .map(c -> c.getOrigin().getPaths().get(0))
                .collect(Collectors.toSet());
        if (off.isEmpty()) return;
        try {
            Method find = ResourcePackLoader.class.getDeclaredMethod("findResourcePacks");
            find.setAccessible(true);
            find.invoke(null);
            Field f = ResourcePackLoader.class.getDeclaredField("modResourcePacks");
            f.setAccessible(true);
            Map<?, ?> packs = (Map<?, ?>) f.get(null);
            int before = packs.size();
            packs.keySet().removeIf(k -> k instanceof net.neoforged.neoforgespi.locating.IModFile mf && off.contains(mf.getFilePath()));
            LOG.info("[Omniloader] Hid the data and textures of {} switched-off mod(s)", before - packs.size());
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("[Omniloader] Could not hide switched-off mods' data; their recipes/worldgen may cause errors: {}", e.toString());
        }
    }
}
