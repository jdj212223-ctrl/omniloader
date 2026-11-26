package dev.omniloader.engine;

import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforgespi.language.IModInfo;

/** Minecraft itself, as an entry in the mod list. */
final class MinecraftModContainer extends ModContainer {
    private final IEventBus bus = BusBuilder.builder().markerType(IModBusEvent.class).allowPerPhasePost().build();
    MinecraftModContainer(IModInfo info) { super(info); }
    @Override public IEventBus getEventBus() { return bus; }
}
