package net.fabricmc.fabric.api.networking.v1;

import dev.omniloader.runtime.fabricapi.NetworkBridge;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.network.PacketDistributor;

/** Fabric API: server side of custom network messages during play. */
public final class ServerPlayNetworking {
    private ServerPlayNetworking() {}

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        return NetworkBridge.SERVER_HANDLERS.putIfAbsent(type.id(), handler) == null;
    }

    public static PlayPayloadHandler<?> unregisterGlobalReceiver(ResourceLocation id) { return NetworkBridge.SERVER_HANDLERS.remove(id); }

    public static <T extends CustomPacketPayload> boolean registerReceiver(ServerGamePacketListenerImpl handler, CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> h) {
        return registerGlobalReceiver(type, h);
    }

    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) { return NetworkBridge.knows(type.id()); }
    public static boolean canSend(ServerGamePacketListenerImpl handler, CustomPacketPayload.Type<?> type) { return NetworkBridge.knows(type.id()); }
    public static boolean canSend(ServerPlayer player, ResourceLocation id) { return NetworkBridge.knows(id); }

    public static Packet<?> createS2CPacket(CustomPacketPayload payload) { return new ClientboundCustomPayloadPacket(payload); }

    public static PacketSender getSender(ServerPlayer player) { return NetworkBridge.sender(player); }
    public static PacketSender getSender(ServerGamePacketListenerImpl handler) { return NetworkBridge.sender(handler.player); }

    public static void send(ServerPlayer player, CustomPacketPayload payload) { PacketDistributor.sendToPlayer(player, payload); }

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public interface Context {
        MinecraftServer server();
        ServerPlayer player();
        PacketSender responseSender();
    }
}
