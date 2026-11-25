package dev.omniloader.runtime.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Fabric API (26.1) adds checkPermission(node, fallbackLevel) to command sources; Fabric mods like Chunky call it.
 * Without a permissions mod, Fabric answers with the vanilla level check, and so do we.
 */
@Mixin(CommandSourceStack.class)
public abstract class CommandSourceStackMixin {
    @Shadow public abstract PermissionSet permissions();

    public boolean checkPermission(Identifier permission, PermissionLevel fallback) {
        return permissions().hasPermission(new Permission.HasCommandLevel(fallback));
    }

    /** Without a permissions mod, Fabric API answers with the default the mod passes in. */
    public boolean checkPermission(Identifier permission, boolean defaultValue) {
        return defaultValue;
    }
}
