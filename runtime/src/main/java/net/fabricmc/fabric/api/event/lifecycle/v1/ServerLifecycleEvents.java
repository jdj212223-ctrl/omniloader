package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.CloseableResourceManager;

/** Fabric API server lifecycle events (Omniloader stand-in, fired from NeoForge's server events). */
public final class ServerLifecycleEvents {
    private ServerLifecycleEvents() {}

    public static final Event<ServerStarting> SERVER_STARTING = EventFactory.createArrayBacked(ServerStarting.class, cs -> s -> { for (ServerStarting c : cs) c.onServerStarting(s); });
    public static final Event<ServerStarted> SERVER_STARTED = EventFactory.createArrayBacked(ServerStarted.class, cs -> s -> { for (ServerStarted c : cs) c.onServerStarted(s); });
    public static final Event<ServerStopping> SERVER_STOPPING = EventFactory.createArrayBacked(ServerStopping.class, cs -> s -> { for (ServerStopping c : cs) c.onServerStopping(s); });
    public static final Event<ServerStopped> SERVER_STOPPED = EventFactory.createArrayBacked(ServerStopped.class, cs -> s -> { for (ServerStopped c : cs) c.onServerStopped(s); });
    public static final Event<SyncDataPackContents> SYNC_DATA_PACK_CONTENTS = EventFactory.createArrayBacked(SyncDataPackContents.class,
            cs -> (p, j) -> { for (SyncDataPackContents c : cs) c.onSyncDataPackContents(p, j); });
    public static final Event<StartDataPackReload> START_DATA_PACK_RELOAD = EventFactory.createArrayBacked(StartDataPackReload.class,
            cs -> (s, m) -> { for (StartDataPackReload c : cs) c.startDataPackReload(s, m); });
    public static final Event<EndDataPackReload> END_DATA_PACK_RELOAD = EventFactory.createArrayBacked(EndDataPackReload.class,
            cs -> (s, m, ok) -> { for (EndDataPackReload c : cs) c.endDataPackReload(s, m, ok); });
    public static final Event<BeforeSave> BEFORE_SAVE = EventFactory.createArrayBacked(BeforeSave.class, cs -> (s, f, x) -> { for (BeforeSave c : cs) c.onBeforeSave(s, f, x); });
    public static final Event<AfterSave> AFTER_SAVE = EventFactory.createArrayBacked(AfterSave.class, cs -> (s, f, x) -> { for (AfterSave c : cs) c.onAfterSave(s, f, x); });

    @FunctionalInterface public interface ServerStarting { void onServerStarting(MinecraftServer server); }
    @FunctionalInterface public interface ServerStarted { void onServerStarted(MinecraftServer server); }
    @FunctionalInterface public interface ServerStopping { void onServerStopping(MinecraftServer server); }
    @FunctionalInterface public interface ServerStopped { void onServerStopped(MinecraftServer server); }
    @FunctionalInterface public interface SyncDataPackContents { void onSyncDataPackContents(ServerPlayer player, boolean joined); }
    @FunctionalInterface public interface StartDataPackReload { void startDataPackReload(MinecraftServer server, CloseableResourceManager resourceManager); }
    @FunctionalInterface public interface EndDataPackReload { void endDataPackReload(MinecraftServer server, CloseableResourceManager resourceManager, boolean success); }
    @FunctionalInterface public interface BeforeSave { void onBeforeSave(MinecraftServer server, boolean flush, boolean force); }
    @FunctionalInterface public interface AfterSave { void onAfterSave(MinecraftServer server, boolean flush, boolean force); }
}
