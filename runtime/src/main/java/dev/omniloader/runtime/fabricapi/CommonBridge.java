package dev.omniloader.runtime.fabricapi;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Fires Fabric API events from the matching NeoForge events. */
public final class CommonBridge {
    private CommonBridge() {}

    public static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(RegisterCommandsEvent.class, e ->
                CommandRegistrationCallback.EVENT.invoker().register(e.getDispatcher(), e.getBuildContext(), e.getCommandSelection()));
        bus.addListener(ServerAboutToStartEvent.class, e -> ServerLifecycleEvents.SERVER_STARTING.invoker().onServerStarting(e.getServer()));
        bus.addListener(ServerStartedEvent.class, e -> ServerLifecycleEvents.SERVER_STARTED.invoker().onServerStarted(e.getServer()));
        bus.addListener(ServerStoppingEvent.class, e -> ServerLifecycleEvents.SERVER_STOPPING.invoker().onServerStopping(e.getServer()));
        bus.addListener(ServerStoppedEvent.class, e -> ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(e.getServer()));
        bus.addListener(OnDatapackSyncEvent.class, e -> {
            boolean joined = e.getPlayer() != null;
            e.getRelevantPlayers().forEach(p -> ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.invoker().onSyncDataPackContents(p, joined));
        });
        bus.addListener(ServerTickEvent.Pre.class, e -> ServerTickEvents.START_SERVER_TICK.invoker().onStartTick(e.getServer()));
        bus.addListener(ServerTickEvent.Post.class, e -> ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(e.getServer()));
        bus.addListener(LevelTickEvent.Pre.class, e -> { if (e.getLevel() instanceof ServerLevel l) ServerTickEvents.START_WORLD_TICK.invoker().onStartTick(l); });
        bus.addListener(LevelTickEvent.Post.class, e -> { if (e.getLevel() instanceof ServerLevel l) ServerTickEvents.END_WORLD_TICK.invoker().onEndTick(l); });
    }
}
