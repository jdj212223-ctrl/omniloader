package dev.omniloader.devtools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

final class Shot {
    static void grab(Minecraft mc, String name, Runnable done) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> done.run());
    }
}
