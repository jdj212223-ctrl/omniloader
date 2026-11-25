package net.fabricmc.fabric.api.client.rendering.v1;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/** Fabric API: draw on the in-game HUD (after the vanilla HUD). Fired from NeoForge's RenderGuiEvent.Post. */
@FunctionalInterface
public interface HudRenderCallback {
    Event<HudRenderCallback> EVENT = EventFactory.createArrayBacked(HudRenderCallback.class, cs -> (g, t) -> { for (HudRenderCallback c : cs) c.onHudRender(g, t); });

    void onHudRender(GuiGraphics drawContext, DeltaTracker tickCounter);
}
