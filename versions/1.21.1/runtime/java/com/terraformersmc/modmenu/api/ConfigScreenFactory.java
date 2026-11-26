package com.terraformersmc.modmenu.api;

import net.minecraft.client.gui.screens.Screen;

/** Mod Menu's API (provided by Omniloader): how a Fabric mod opens its settings screen. */
@FunctionalInterface
public interface ConfigScreenFactory<S extends Screen> {
    S create(Screen parent);
}
