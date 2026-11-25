package dev.omniloader.runtime.fabricapi;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.PackType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fabric resource reload listeners and creative-tab additions, on NeoForge's events. */
public final class ResourceBridge {
    private ResourceBridge() {}

    static final List<IdentifiableResourceReloadListener> SERVER = new CopyOnWriteArrayList<>();
    static final List<IdentifiableResourceReloadListener> CLIENT = new CopyOnWriteArrayList<>();

    public static void add(PackType type, IdentifiableResourceReloadListener l) {
        (type == PackType.SERVER_DATA ? SERVER : CLIENT).add(l);
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, e -> SERVER.forEach(e::addListener));
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, e -> {
            FabricItemGroupEntries entries = new FabricItemGroupEntries(e);
            var perTab = ItemGroupEvents.existing(e.getTabKey());
            if (perTab != null) perTab.invoker().modifyEntries(entries);
            ItemGroupEvents.MODIFY_ENTRIES_ALL.invoker().modifyEntries(e.getTab(), entries);
        });
    }
}
