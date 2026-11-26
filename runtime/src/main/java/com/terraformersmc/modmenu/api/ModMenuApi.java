package com.terraformersmc.modmenu.api;

import com.google.common.collect.ImmutableMap;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.function.Consumer;

/** Mod Menu's API (provided by Omniloader). Fabric mods implement this under their "modmenu" entrypoint. */
public interface ModMenuApi {
    static Screen createModsScreen(Screen previous) { return new net.neoforged.neoforge.client.gui.ModListScreen(previous); }
    static Component createModsButtonText() { return Component.translatable("fml.menu.mods"); }
    default ConfigScreenFactory<?> getModConfigScreenFactory() { return parent -> null; }
    default UpdateChecker getUpdateChecker() { return null; }
    default Map<String, UpdateChecker> getProvidedUpdateCheckers() { return ImmutableMap.of(); }
    default Map<String, ConfigScreenFactory<?>> getProvidedConfigScreenFactories() { return ImmutableMap.of(); }
    default void attachModpackBadges(Consumer<String> consumer) {}
}
