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

    // ---- Forge mods

    /** Forge tolerates registering an object with no event handlers; NeoForge throws. */
    public static void forgeRegister(net.neoforged.bus.api.IEventBus bus, Object target) {
        try { bus.register(target); }
        catch (IllegalArgumentException e) { if (!String.valueOf(e.getMessage()).contains("no @SubscribeEvent")) throw e; }
    }

    public static double forgeScrollY(net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled e) { return e.getScrollDeltaY(); }
    public static double forgeScrollX(net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled e) { return e.getScrollDeltaX(); }

    public static void forgeConfigScreen(java.util.function.Function<net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen> f) {
        forgeConfigScreen((java.util.function.BiFunction<net.minecraft.client.Minecraft, net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen>) (mc, s) -> f.apply(s));
    }

    public static void forgeConfigScreen(java.util.function.BiFunction<net.minecraft.client.Minecraft, net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen> f) {
        var c = net.neoforged.fml.ModLoadingContext.get().getActiveContainer();
        dev.omniloader.runtime.forge.ForgeExtensionPoints.register(c, net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(f));
    }

    /** Forge's ForgeSpawnEggItem.fromEntityType: NeoForge's deferred spawn eggs, then vanilla's. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static net.minecraft.world.item.SpawnEggItem forgeSpawnEgg(net.minecraft.world.entity.EntityType type) {
        var e = net.neoforged.neoforge.common.DeferredSpawnEggItem.deferredOnlyById(type);
        return e != null ? e : net.minecraft.world.item.SpawnEggItem.byId(type);
    }

    public static Holder<?> unused() { return null; }
}
