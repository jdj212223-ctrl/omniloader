package dev.omniloader.loader;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** What the early scan of the mods folder learned, shared between Omniloader's discovery plugins. */
public final class OmniState {
    private OmniState() {}

    /** Mod ids (and "provides" aliases) that exist in this game, from any loader. */
    public static final Set<String> presentIds = ConcurrentHashMap.newKeySet();
    /** Original Fabric/Quilt mod id -> human reason why it was switched off. */
    public static final Map<String, String> disabled = new ConcurrentHashMap<>();

    /** Ids that Omniloader itself stands in for. Phase 3 adds Fabric API modules here as they are shimmed. */
    public static final Set<String> BUILT_IN = Set.of(
            "minecraft", "java", "fabricloader", "fabric-loader", "quilt_loader", "neoforge", "omniloader",
            "mixinextras");

    /** Nested jars we never load, because Omniloader or NeoForge already provide them. */
    public static boolean skipNested(String id) {
        return id.equals("fabricloader") || id.equals("fabric-loader") || id.equals("mixinextras")
                || id.equals("fabric-api") || id.equals("fabric") || id.startsWith("fabric-") && !id.startsWith("fabric-language-")
                || id.equals("quilt_loader") || id.equals("qsl") || id.equals("quilted_fabric_api");
    }

    public static boolean isFabricApiId(String id) {
        return id.equals("fabric-api") || id.equals("fabric") || id.startsWith("fabric-") && !id.startsWith("fabric-language-")
                || id.equals("qsl") || id.equals("quilted_fabric_api") || id.startsWith("quilt_") && !id.equals("quilt_loader");
    }
}
