package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Fabric API server tick events (Omniloader stand-in). */
public final class ServerTickEvents {
    private ServerTickEvents() {}

    public static final Event<StartTick> START_SERVER_TICK = EventFactory.createArrayBacked(StartTick.class, cs -> s -> { for (StartTick c : cs) c.onStartTick(s); });
    public static final Event<EndTick> END_SERVER_TICK = EventFactory.createArrayBacked(EndTick.class, cs -> s -> { for (EndTick c : cs) c.onEndTick(s); });
    public static final Event<StartWorldTick> START_WORLD_TICK = EventFactory.createArrayBacked(StartWorldTick.class, cs -> w -> { for (StartWorldTick c : cs) c.onStartTick(w); });
    public static final Event<EndWorldTick> END_WORLD_TICK = EventFactory.createArrayBacked(EndWorldTick.class, cs -> w -> { for (EndWorldTick c : cs) c.onEndTick(w); });

    @FunctionalInterface public interface StartTick { void onStartTick(MinecraftServer server); }
    @FunctionalInterface public interface EndTick { void onEndTick(MinecraftServer server); }
    @FunctionalInterface public interface StartWorldTick { void onStartTick(ServerLevel world); }
    @FunctionalInterface public interface EndWorldTick { void onEndTick(ServerLevel world); }
}
