package net.fabricmc.fabric.api.client.keybinding.v1;

import com.mojang.blaze3d.platform.InputConstants;
import dev.omniloader.runtime.fabricapi.ClientBridge;
import net.minecraft.client.KeyMapping;

/** Fabric API key bindings (Omniloader stand-in): handed to NeoForge's key mapping registration. */
public final class KeyBindingHelper {
    private KeyBindingHelper() {}

    public static KeyMapping registerKeyBinding(KeyMapping keyBinding) { return ClientBridge.registerKey(keyBinding); }

    public static InputConstants.Key getBoundKeyOf(KeyMapping keyBinding) { return keyBinding.getKey(); }
}
