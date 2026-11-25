package dev.omniloader.runtime.fabricapi;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Fabric's networking, on NeoForge's networking. Fabric mods declare message types and handlers while starting;
 * we collect them and hand them to NeoForge when it asks (RegisterPayloadHandlersEvent).
 */
public final class NetworkBridge {
    private static final Logger LOG = LoggerFactory.getLogger("Omniloader");

    public enum Kind { PLAY_C2S, PLAY_S2C, CONFIG_C2S, CONFIG_S2C }

    private record Entry(CustomPacketPayload.Type<?> type, StreamCodec<?, ?> codec) {}

    private static final Map<Kind, Map<ResourceLocation, Entry>> TYPES = new ConcurrentHashMap<>();
    public static final Map<ResourceLocation, ServerPlayNetworking.PlayPayloadHandler<?>> SERVER_HANDLERS = new ConcurrentHashMap<>();
    /** Client handlers, kept untyped so this class never touches client-only code. */
    public static final Map<ResourceLocation, Object> CLIENT_HANDLERS = new ConcurrentHashMap<>();
    /** Set by the client half: runs a client handler. */
    public static volatile BiConsumer<CustomPacketPayload, IPayloadContext> clientDispatch;

    private NetworkBridge() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <B extends FriendlyByteBuf> PayloadTypeRegistry<B> registry(Kind kind) {
        return new PayloadTypeRegistry<>() {
            @Override
            public <T extends CustomPacketPayload> CustomPacketPayload.TypeAndCodec<? super B, T> register(CustomPacketPayload.Type<T> id, StreamCodec<? super B, T> codec) {
                Entry prev = TYPES.computeIfAbsent(kind, k -> new ConcurrentHashMap<>()).putIfAbsent(id.id(), new Entry(id, codec));
                if (prev != null) throw new IllegalArgumentException("Packet type " + id + " is already registered");
                return (CustomPacketPayload.TypeAndCodec) new CustomPacketPayload.TypeAndCodec(id, (StreamCodec) codec);
            }
        };
    }

    public static boolean knows(ResourceLocation id) {
        return TYPES.values().stream().anyMatch(m -> m.containsKey(id));
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, NetworkBridge::register);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p)
                ServerPlayConnectionEvents.JOIN.invoker().onPlayReady(p.connection, sender(p), p.server);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p && p.connection != null)
                ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.connection, p.server);
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("omniloader").optional();
        Map<ResourceLocation, Entry> c2s = TYPES.getOrDefault(Kind.PLAY_C2S, Map.of()), s2c = TYPES.getOrDefault(Kind.PLAY_S2C, Map.of());
        Map<ResourceLocation, Entry> all = new LinkedHashMap<>(c2s);
        s2c.forEach(all::putIfAbsent);
        int n = 0;
        for (var en : all.entrySet()) {
            ResourceLocation id = en.getKey();
            CustomPacketPayload.Type type = en.getValue().type();
            StreamCodec codec = en.getValue().codec();
            boolean toServer = c2s.containsKey(id), toClient = s2c.containsKey(id);
            if (toServer && toClient) r.playBidirectional(type, codec, (p, ctx) -> dispatch((CustomPacketPayload) p, ctx));
            else if (toServer) r.playToServer(type, codec, (p, ctx) -> dispatch((CustomPacketPayload) p, ctx));
            else r.playToClient(type, codec, (p, ctx) -> dispatch((CustomPacketPayload) p, ctx));
            n++;
        }
        for (Kind k : new Kind[]{Kind.CONFIG_C2S, Kind.CONFIG_S2C}) {
            for (Entry en : TYPES.getOrDefault(k, Map.of()).values()) {
                if (k == Kind.CONFIG_C2S) r.configurationToServer((CustomPacketPayload.Type) en.type(), (StreamCodec) en.codec(), (p, ctx) -> {});
                else r.configurationToClient((CustomPacketPayload.Type) en.type(), (StreamCodec) en.codec(), (p, ctx) -> {});
                n++;
            }
        }
        if (n > 0) LOG.info("[Omniloader] Registered {} Fabric network message type(s) with NeoForge", n);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void dispatch(CustomPacketPayload payload, IPayloadContext ctx) {
        ResourceLocation id = payload.type().id();
        if (ctx.player() instanceof ServerPlayer sp) {
            ServerPlayNetworking.PlayPayloadHandler h = SERVER_HANDLERS.get(id);
            if (h == null) return;
            PacketSender sender = sender(sp);
            h.receive(payload, new ServerPlayNetworking.Context() {
                public MinecraftServer server() { return sp.server; }
                public ServerPlayer player() { return sp; }
                public PacketSender responseSender() { return sender; }
            });
        } else if (clientDispatch != null) {
            clientDispatch.accept(payload, ctx);
        }
    }

    public static PacketSender sender(ServerPlayer player) {
        return new PacketSender() {
            public Packet<?> createPacket(CustomPacketPayload payload) { return new ClientboundCustomPayloadPacket(payload); }
            public void sendPacket(Packet<?> packet) { player.connection.send(packet); }
            public void disconnect(Component reason) { player.connection.disconnect(reason); }
        };
    }
}
