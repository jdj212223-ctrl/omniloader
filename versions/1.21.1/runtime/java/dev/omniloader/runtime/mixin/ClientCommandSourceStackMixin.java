package dev.omniloader.runtime.mixin;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.ClientCommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * NeoForge's client command source also becomes Fabric's kind of client command source,
 * so Fabric mods' client commands plug straight into NeoForge's client command system.
 */
@Mixin(ClientCommandSourceStack.class)
public abstract class ClientCommandSourceStackMixin implements FabricClientCommandSource {
    @Override public void sendFeedback(Component message) { Minecraft.getInstance().gui.getChat().addMessage(message); }
    @Override public void sendError(Component message) { Minecraft.getInstance().gui.getChat().addMessage(message.copy().withStyle(ChatFormatting.RED)); }
    @Override public Minecraft getClient() { return Minecraft.getInstance(); }
    @Override public LocalPlayer getPlayer() { return Minecraft.getInstance().player; }
    @Override public ClientLevel getWorld() { return Minecraft.getInstance().level; }
}
