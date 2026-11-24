package net.fabricmc.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Fabric API: register server commands. Fired from NeoForge's RegisterCommandsEvent. */
@FunctionalInterface
public interface CommandRegistrationCallback {
    Event<CommandRegistrationCallback> EVENT = EventFactory.createArrayBacked(CommandRegistrationCallback.class, callbacks -> (dispatcher, context, selection) -> {
        for (CommandRegistrationCallback c : callbacks) c.register(dispatcher, context, selection);
    });

    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment);
}
