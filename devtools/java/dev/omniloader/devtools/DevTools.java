package dev.omniloader.devtools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.File;

/** Test helper for the dev game only (never shipped): saves a screenshot of the title screen, then optionally quits. */
@Mod(value = "omnidevtools", dist = Dist.CLIENT)
public final class DevTools {
    private long firstSeen = -1;
    private boolean done;

    private int worldTicks;

    public DevTools() {
        if (Boolean.getBoolean("omniloader.joinShot")) {
            // Join test: once in a world for ~5 seconds, save a screenshot and quit.
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class, e -> {
                Minecraft mc = Minecraft.getInstance();
                if (done || mc.level == null || mc.player == null) return;
                if (++worldTicks == 100) {
                    done = true;
                    System.out.println("[DevTools] in world, players online: " + mc.getConnection().getOnlinePlayers().size());
                    var names = new java.util.TreeSet<String>();
                    net.neoforged.neoforge.client.ClientCommandHandler.getDispatcher().getRoot().getChildren().forEach(n -> names.add(n.getName()));
                    System.out.println("[DevTools] client commands: " + names);
                    Shot.grab(mc, "omniloader-join.png", () -> { System.out.println("[DevTools] join screenshot saved"); mc.stop(); });
                }
            });
            return;
        }
        if (!Boolean.getBoolean("omniloader.titleShot")) return;
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, e -> {
            if (done || !(e.getScreen() instanceof TitleScreen)) return;
            if (firstSeen < 0) firstSeen = System.currentTimeMillis();
            if (System.currentTimeMillis() - firstSeen < 4000) return;
            done = true;
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> Shot.grab(mc, "omniloader-title.png", () -> {
                System.out.println("[DevTools] title screenshot saved");
                if (Boolean.getBoolean("omniloader.quitAfterShot")) mc.stop();
            }));
        });
    }
}
