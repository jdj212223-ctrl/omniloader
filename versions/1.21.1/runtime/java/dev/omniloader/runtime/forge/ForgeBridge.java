package dev.omniloader.runtime.forge;

import net.minecraftforge.event.TickEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Fires Forge-style events for Forge mods from NeoForge's. Only started when Forge mods are present. */
public final class ForgeBridge {
    private ForgeBridge() {}

    public static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ServerTickEvent.Pre.class, e -> bus.post(new TickEvent.ServerTickEvent.Pre(e::hasTime, e.getServer())));
        bus.addListener(ServerTickEvent.Post.class, e -> bus.post(new TickEvent.ServerTickEvent.Post(e::hasTime, e.getServer())));
        bus.addListener(LevelTickEvent.Pre.class, e -> bus.post(new TickEvent.LevelTickEvent.Pre(side(e.getLevel().isClientSide()), e.getLevel(), e::hasTime)));
        bus.addListener(LevelTickEvent.Post.class, e -> bus.post(new TickEvent.LevelTickEvent.Post(side(e.getLevel().isClientSide()), e.getLevel(), e::hasTime)));
        bus.addListener(PlayerTickEvent.Pre.class, e -> bus.post(new TickEvent.PlayerTickEvent.Pre(e.getEntity())));
        bus.addListener(PlayerTickEvent.Post.class, e -> bus.post(new TickEvent.PlayerTickEvent.Post(e.getEntity())));
        if (FMLEnvironment.dist.isClient()) Client.init();
    }

    private static LogicalSide side(boolean client) { return client ? LogicalSide.CLIENT : LogicalSide.SERVER; }

    static final class Client {
        static void init() {
            var bus = NeoForge.EVENT_BUS;
            bus.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Pre.class, e -> bus.post(new TickEvent.ClientTickEvent.Pre()));
            bus.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class, e -> bus.post(new TickEvent.ClientTickEvent.Post()));
            bus.addListener(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre.class, e -> bus.post(new TickEvent.RenderTickEvent.Pre(e.getPartialTick().getGameTimeDeltaPartialTick(true))));
            bus.addListener(net.neoforged.neoforge.client.event.RenderFrameEvent.Post.class, e -> bus.post(new TickEvent.RenderTickEvent.Post(e.getPartialTick().getGameTimeDeltaPartialTick(true))));
        }
    }
}
