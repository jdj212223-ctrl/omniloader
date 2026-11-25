package net.fabricmc.fabric.api.client.networking.v1;

import dev.omniloader.runtime.fabricapi.ClientNetworkBridge;
import dev.omniloader.runtime.fabricapi.NetworkBridge;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/** Fabric API: client side of custom network messages during play. */
public final class ClientPlayNetworking {
    private ClientPlayNetworking() {}

    public static <T extends CustomPacketPayload> boolean registerGlobalReceiver(CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        return NetworkBridge.CLIENT_HANDLERS.putIfAbsent(type.id(), handler) == null;
    }

    public static PlayPayloadHandler<?> unregisterGlobalReceiver(ResourceLocation id) { return (PlayPayloadHandler<?>) NetworkBridge.CLIENT_HANDLERS.remove(id); }

    public static <T extends CustomPacketPayload> boolean registerReceiver(CustomPacketPayload.Type<T> type, PlayPayloadHandler<T> handler) {
        return registerGlobalReceiver(type, handler);
    }

    public static boolean canSend(CustomPacketPayload.Type<?> type) { return Minecraft.getInstance().getConnection() != null && NetworkBridge.knows(type.id()); }
    public static boolean canSend(ResourceLocation id) { return Minecraft.getInstance().getConnection() != null && NetworkBridge.knows(id); }

    public static Packet<?> createC2SPacket(CustomPacketPayload payload) { return new ServerboundCustomPayloadPacket(payload); }

    public static PacketSender getSender() { return ClientNetworkBridge.sender(); }

    public static void send(CustomPacketPayload payload) { PacketDistributor.sendToServer(payload); }

    @FunctionalInterface
    public interface PlayPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, Context context);
    }

    public interface Context {
        Minecraft client();
        LocalPlayer player();
        PacketSender responseSender();
    }
}
