package net.minecraftforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.BiFunction;
import java.util.function.Function;

/** Forge's config screen hookup (Omniloader stand-in: becomes NeoForge's config screen). */
public final class ConfigScreenHandler {
    private ConfigScreenHandler() {}

    public record ConfigScreenFactory(BiFunction<Minecraft, Screen, Screen> screenFunction) implements net.neoforged.fml.IExtensionPoint {
        public ConfigScreenFactory(Function<Screen, Screen> screenFunction) { this((mc, s) -> screenFunction.apply(s)); }
    }
}
