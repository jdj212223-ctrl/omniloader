package net.fabricmc.fabric.api.client.networking.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Fabric API: the client joined / left a world or server. */
public final class ClientPlayConnectionEvents {
    private ClientPlayConnectionEvents() {}

    public static final Event<Init> INIT = EventFactory.createArrayBacked(Init.class, cs -> (h, c) -> { for (Init x : cs) x.onPlayInit(h, c); });
    public static final Event<Join> JOIN = EventFactory.createArrayBacked(Join.class, cs -> (h, s, c) -> { for (Join x : cs) x.onPlayReady(h, s, c); });
    public static final Event<Disconnect> DISCONNECT = EventFactory.createArrayBacked(Disconnect.class, cs -> (h, c) -> { for (Disconnect x : cs) x.onPlayDisconnect(h, c); });

    @FunctionalInterface public interface Init { void onPlayInit(ClientPacketListener handler, Minecraft client); }
    @FunctionalInterface public interface Join { void onPlayReady(ClientPacketListener handler, PacketSender sender, Minecraft client); }
    @FunctionalInterface public interface Disconnect { void onPlayDisconnect(ClientPacketListener handler, Minecraft client); }
}
