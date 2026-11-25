package dev.omniloader.runtime.fabricapi;

import dev.omniloader.runtime.Platform;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/** Networking, creative tabs, resource reloading and HUD drawing for Fabric mods. */
public final class FabricApiExtras {
    private FabricApiExtras() {}

    public static void init(IEventBus modBus) {
        NetworkBridge.init(modBus);
        ResourceBridge.init(modBus);
        GameEventsBridge.init();
        if (Platform.isClient()) Client.init(modBus);
    }

    /** Client-only part (separate class so servers never load it). */
    static final class Client {
        static void init(IEventBus modBus) {
            ClientNetworkBridge.init();
            ScreenBridge.init();
            modBus.addListener(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent.class,
                    e -> ResourceBridge.CLIENT.forEach(e::registerReloadListener));
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderGuiEvent.Post.class,
                    e -> net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.invoker().onHudRender(e.getGuiGraphics(), e.getPartialTick()));
        }
    }
}
