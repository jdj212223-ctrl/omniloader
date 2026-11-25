package net.fabricmc.fabric.api.itemgroup.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fabric API: add items to creative tabs. Fired from NeoForge's BuildCreativeModeTabContentsEvent. */
public final class ItemGroupEvents {
    private ItemGroupEvents() {}

    private static final Map<ResourceKey<CreativeModeTab>, Event<ModifyEntries>> PER_TAB = new ConcurrentHashMap<>();

    public static final Event<ModifyEntriesAll> MODIFY_ENTRIES_ALL = EventFactory.createArrayBacked(ModifyEntriesAll.class,
            cs -> (g, e) -> { for (ModifyEntriesAll c : cs) c.modifyEntries(g, e); });

    public static Event<ModifyEntries> modifyEntriesEvent(ResourceKey<CreativeModeTab> key) {
        return PER_TAB.computeIfAbsent(key, k -> EventFactory.createArrayBacked(ModifyEntries.class, cs -> e -> { for (ModifyEntries c : cs) c.modifyEntries(e); }));
    }

    /** Omniloader: the per-tab event, only if some mod asked for it. */
    public static Event<ModifyEntries> existing(ResourceKey<CreativeModeTab> key) { return PER_TAB.get(key); }

    @FunctionalInterface public interface ModifyEntries { void modifyEntries(FabricItemGroupEntries entries); }
    @FunctionalInterface public interface ModifyEntriesAll { void modifyEntries(CreativeModeTab group, FabricItemGroupEntries entries); }
}
