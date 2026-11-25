package dev.omniloader.runtime.backport;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.LevelHeightAccessor;

import java.util.Optional;
import java.util.Set;

/**
 * Stand-ins for things Mojang added to Minecraft after 1.21.1, so mods made for newer versions can run here.
 * Omniloader rewrites a newer mod's calls to point at these (see backports.txt in the loader).
 * Each method takes the object the mod called on as its first argument.
 */
public final class Backports {
    private Backports() {}

    // 1.21.2: LevelHeightAccessor.getMinY()/getMaxY() replaced getMinBuildHeight()/getMaxBuildHeight() (max is now inclusive).
    public static int getMinY(LevelHeightAccessor level) { return level.getMinBuildHeight(); }
    public static int getMaxY(LevelHeightAccessor level) { return level.getMaxBuildHeight() - 1; }

    // 1.21.2: HolderLookup.Provider.get(ResourceKey) finds an entry in whichever registry the key belongs to.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Optional get(RegistryAccess access, ResourceKey key) {
        return access.registry(ResourceKey.createRegistryKey(key.registry())).flatMap(r -> ((net.minecraft.core.Registry) r).getHolder(key));
    }

    // 1.21.2: teleportTo gained a "reset camera" flag (and Relative replaced RelativeMovement).
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean teleportTo(ServerPlayer player, ServerLevel level, double x, double y, double z, Set relative, float yRot, float xRot, boolean resetCamera) {
        return player.teleportTo(level, x, y, z, (Set<RelativeMovement>) relative, yRot, xRot);
    }

    public static Holder<?> unused() { return null; }
}
