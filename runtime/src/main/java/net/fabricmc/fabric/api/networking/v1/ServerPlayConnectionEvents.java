package net.fabricmc.fabric.api.networking.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Fabric API: a player joined / left the server. */
public final class ServerPlayConnectionEvents {
    private ServerPlayConnectionEvents() {}

    public static final Event<Init> INIT = EventFactory.createArrayBacked(Init.class, cs -> (h, s) -> { for (Init c : cs) c.onPlayInit(h, s); });
    public static final Event<Join> JOIN = EventFactory.createArrayBacked(Join.class, cs -> (h, p, s) -> { for (Join c : cs) c.onPlayReady(h, p, s); });
    public static final Event<Disconnect> DISCONNECT = EventFactory.createArrayBacked(Disconnect.class, cs -> (h, s) -> { for (Disconnect c : cs) c.onPlayDisconnect(h, s); });

    @FunctionalInterface public interface Init { void onPlayInit(ServerGamePacketListenerImpl handler, MinecraftServer server); }
    @FunctionalInterface public interface Join { void onPlayReady(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server); }
    @FunctionalInterface public interface Disconnect { void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server); }
}
