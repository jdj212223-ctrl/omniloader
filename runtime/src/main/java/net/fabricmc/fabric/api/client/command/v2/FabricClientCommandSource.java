package net.fabricmc.fabric.api.client.command.v2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Fabric API: who ran a client command. On NeoForge, its client command source is taught to be one of these. */
public interface FabricClientCommandSource extends SharedSuggestionProvider {
    void sendFeedback(Component message);
    void sendError(Component message);
    Minecraft getClient();
    LocalPlayer getPlayer();
    default Entity getEntity() { return getPlayer(); }
    default Vec3 getPosition() { return getPlayer().position(); }
    default Vec2 getRotation() { return getPlayer().getRotationVector(); }
    ClientLevel getWorld();
    default Object getMeta(String key) { return null; }
}
