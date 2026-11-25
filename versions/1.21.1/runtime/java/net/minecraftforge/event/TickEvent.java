package net.minecraftforge.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.LogicalSide;

import java.util.function.BooleanSupplier;

/** Forge's tick events (Omniloader stand-in, fired from NeoForge's tick events). */
public abstract class TickEvent extends Event {
    public enum Type { LEVEL, PLAYER, CLIENT, SERVER, RENDER }
    public enum Phase { START, END }

    public final Type type;
    public final LogicalSide side;
    public final Phase phase;

    protected TickEvent(Type type, LogicalSide side, Phase phase) { this.type = type; this.side = side; this.phase = phase; }

    public static class ServerTickEvent extends TickEvent {
        private final BooleanSupplier haveTime; private final MinecraftServer server;
        public ServerTickEvent(Phase phase, BooleanSupplier haveTime, MinecraftServer server) { super(Type.SERVER, LogicalSide.SERVER, phase); this.haveTime = haveTime; this.server = server; }
        public MinecraftServer getServer() { return server; }
        public boolean haveTime() { return haveTime.getAsBoolean(); }
        public static class Pre extends ServerTickEvent { public Pre(BooleanSupplier h, MinecraftServer s) { super(Phase.START, h, s); } }
        public static class Post extends ServerTickEvent { public Post(BooleanSupplier h, MinecraftServer s) { super(Phase.END, h, s); } }
    }

    public static class ClientTickEvent extends TickEvent {
        public ClientTickEvent(Phase phase) { super(Type.CLIENT, LogicalSide.CLIENT, phase); }
        public static class Pre extends ClientTickEvent { public static final Pre INSTANCE = new Pre(); public Pre() { super(Phase.START); } }
        public static class Post extends ClientTickEvent { public static final Post INSTANCE = new Post(); public Post() { super(Phase.END); } }
    }

    public static class LevelTickEvent extends TickEvent {
        public final Level level; private final BooleanSupplier haveTime;
        public LevelTickEvent(LogicalSide side, Phase phase, Level level, BooleanSupplier haveTime) { super(Type.LEVEL, side, phase); this.level = level; this.haveTime = haveTime; }
        public boolean haveTime() { return haveTime.getAsBoolean(); }
        public static class Pre extends LevelTickEvent { public Pre(LogicalSide s, Level l, BooleanSupplier h) { super(s, Phase.START, l, h); } }
        public static class Post extends LevelTickEvent { public Post(LogicalSide s, Level l, BooleanSupplier h) { super(s, Phase.END, l, h); } }
    }

    public static class PlayerTickEvent extends TickEvent {
        public final Player player;
        public PlayerTickEvent(Phase phase, Player player) { super(Type.PLAYER, player.level().isClientSide ? LogicalSide.CLIENT : LogicalSide.SERVER, phase); this.player = player; }
        public static class Pre extends PlayerTickEvent { public Pre(Player p) { super(Phase.START, p); } }
        public static class Post extends PlayerTickEvent { public Post(Player p) { super(Phase.END, p); } }
    }

    public static class RenderTickEvent extends TickEvent {
        private final float partialTick;
        public RenderTickEvent(Phase phase, float partialTick) { super(Type.RENDER, LogicalSide.CLIENT, phase); this.partialTick = partialTick; }
        public float getPartialTick() { return partialTick; }
        public static class Pre extends RenderTickEvent { public Pre(float t) { super(Phase.START, t); } }
        public static class Post extends RenderTickEvent { public Post(float t) { super(Phase.END, t); } }
    }
}
