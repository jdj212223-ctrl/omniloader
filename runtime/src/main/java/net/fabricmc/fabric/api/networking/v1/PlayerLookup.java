package net.fabricmc.fabric.api.networking.v1;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Fabric API: find players (all, in a world, near a spot, or that can see something). */
public final class PlayerLookup {
    private PlayerLookup() {}

    public static Collection<ServerPlayer> all(MinecraftServer server) { return List.copyOf(server.getPlayerList().getPlayers()); }
    public static Collection<ServerPlayer> world(ServerLevel world) { return List.copyOf(world.players()); }
    public static Collection<ServerPlayer> tracking(ServerLevel world, ChunkPos pos) { return List.copyOf(world.getChunkSource().chunkMap.getPlayers(pos, false)); }
    public static Collection<ServerPlayer> tracking(ServerLevel world, BlockPos pos) { return tracking(world, new ChunkPos(pos)); }
    public static Collection<ServerPlayer> tracking(BlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel sl)) throw new IllegalArgumentException("Only supported on server worlds!");
        return tracking(sl, be.getBlockPos());
    }
    public static Collection<ServerPlayer> tracking(Entity entity) {
        if (!(entity.level() instanceof ServerLevel sl)) throw new IllegalArgumentException("Only supported on server worlds!");
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : sl.players()) if (p != entity && sl.getChunkSource().chunkMap.getPlayers(entity.chunkPosition(), false).contains(p)) out.add(p);
        return out;
    }
    public static Collection<ServerPlayer> around(ServerLevel world, Vec3 pos, double radius) {
        double r2 = radius * radius;
        return world.players().stream().filter(p -> p.distanceToSqr(pos) <= r2).toList();
    }
    public static Collection<ServerPlayer> around(ServerLevel world, Vec3i pos, double radius) { return around(world, Vec3.atCenterOf(pos), radius); }
}
