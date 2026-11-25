package dev.omniloader.runtime.fabricapi;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client half of Fabric networking. Only loaded on the client. */
public final class ClientNetworkBridge {
    private ClientNetworkBridge() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void init() {
        NetworkBridge.clientDispatch = (payload, ctx) -> {
            ClientPlayNetworking.PlayPayloadHandler h = (ClientPlayNetworking.PlayPayloadHandler) NetworkBridge.CLIENT_HANDLERS.get(payload.type().id());
            if (h == null) return;
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            PacketSender s = sender();
            h.receive(payload, new ClientPlayNetworking.Context() {
                public Minecraft client() { return mc; }
                public LocalPlayer player() { return player; }
                public PacketSender responseSender() { return s; }
            });
        };
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, e -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) ClientPlayConnectionEvents.JOIN.invoker().onPlayReady(conn, sender(), Minecraft.getInstance());
        });
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) ClientPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(conn, Minecraft.getInstance());
        });
    }

    public static PacketSender sender() {
        return new PacketSender() {
            public Packet<?> createPacket(CustomPacketPayload payload) { return new ServerboundCustomPayloadPacket(payload); }
            public void sendPacket(Packet<?> packet) { var c = Minecraft.getInstance().getConnection(); if (c != null) c.send(packet); }
            public void disconnect(Component reason) { var c = Minecraft.getInstance().getConnection(); if (c != null) c.getConnection().disconnect(reason); }
        };
    }
}
