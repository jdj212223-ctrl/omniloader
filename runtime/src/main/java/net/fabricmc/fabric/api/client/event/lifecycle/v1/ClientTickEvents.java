package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** Fabric API client tick events (Omniloader stand-in). */
public final class ClientTickEvents {
    private ClientTickEvents() {}

    public static final Event<StartTick> START_CLIENT_TICK = EventFactory.createArrayBacked(StartTick.class, cs -> m -> { for (StartTick c : cs) c.onStartTick(m); });
    public static final Event<EndTick> END_CLIENT_TICK = EventFactory.createArrayBacked(EndTick.class, cs -> m -> { for (EndTick c : cs) c.onEndTick(m); });
    public static final Event<StartWorldTick> START_WORLD_TICK = EventFactory.createArrayBacked(StartWorldTick.class, cs -> w -> { for (StartWorldTick c : cs) c.onStartTick(w); });
    public static final Event<EndWorldTick> END_WORLD_TICK = EventFactory.createArrayBacked(EndWorldTick.class, cs -> w -> { for (EndWorldTick c : cs) c.onEndTick(w); });

    @FunctionalInterface public interface StartTick { void onStartTick(Minecraft client); }
    @FunctionalInterface public interface EndTick { void onEndTick(Minecraft client); }
    @FunctionalInterface public interface StartWorldTick { void onStartTick(ClientLevel world); }
    @FunctionalInterface public interface EndWorldTick { void onEndTick(ClientLevel world); }
}
