package net.fabricmc.fabric.api.networking.v1;

import dev.omniloader.runtime.fabricapi.NetworkBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Fabric API: declares which custom network messages exist and how to read/write them. Handed to NeoForge. */
public interface PayloadTypeRegistry<B extends FriendlyByteBuf> {
    <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(CustomPacketPayload.Type<T> id, StreamCodec<? super B, T> codec);

    static PayloadTypeRegistry<RegistryFriendlyByteBuf> playC2S() { return NetworkBridge.registry(NetworkBridge.Kind.PLAY_C2S); }
    static PayloadTypeRegistry<RegistryFriendlyByteBuf> playS2C() { return NetworkBridge.registry(NetworkBridge.Kind.PLAY_S2C); }
    static PayloadTypeRegistry<FriendlyByteBuf> configurationC2S() { return NetworkBridge.registry(NetworkBridge.Kind.CONFIG_C2S); }
    static PayloadTypeRegistry<FriendlyByteBuf> configurationS2C() { return NetworkBridge.registry(NetworkBridge.Kind.CONFIG_S2C); }
}
