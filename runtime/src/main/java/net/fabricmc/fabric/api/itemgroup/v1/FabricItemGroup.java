package net.fabricmc.fabric.api.itemgroup.v1;

import net.minecraft.world.item.CreativeModeTab;

/** Fabric API: start building a new creative tab (register it in the creative tab registry). */
public final class FabricItemGroup {
    private FabricItemGroup() {}

    public static CreativeModeTab.Builder builder() { return CreativeModeTab.builder(); }
}
