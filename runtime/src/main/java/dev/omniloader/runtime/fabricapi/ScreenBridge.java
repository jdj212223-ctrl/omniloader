package dev.omniloader.runtime.fabricapi;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.Event;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/** Fabric API screen events, client commands and client start/stop, fired from NeoForge's. Client only. */
public final class ScreenBridge {
    private ScreenBridge() {}
    private static boolean started;

    private static <T> T per(Screen s, String key) {
        Event<T> e = ScreenEvents.peek(s, key);
        if (e == null) e = ScreenKeyboardEvents.peek(s, key);
        if (e == null) e = ScreenMouseEvents.peek(s, key);
        return e == null ? null : e.invoker();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void init() {
        var bus = NeoForge.EVENT_BUS;
        Minecraft mc = Minecraft.getInstance();
        bus.addListener(ScreenEvent.Init.Pre.class, e -> {
            Screen s = e.getScreen();
            ScreenEvents.clear(s); ScreenKeyboardEvents.clear(s); ScreenMouseEvents.clear(s);
            ScreenEvents.BEFORE_INIT.invoker().beforeInit(mc, s, s.width, s.height);
        });
        bus.addListener(ScreenEvent.Init.Post.class, e -> ScreenEvents.AFTER_INIT.invoker().afterInit(mc, e.getScreen(), e.getScreen().width, e.getScreen().height));
        bus.addListener(ScreenEvent.Closing.class, e -> { ScreenEvents.Remove r = per(e.getScreen(), "remove"); if (r != null) r.onRemove(e.getScreen()); });
        bus.addListener(ScreenEvent.Render.Pre.class, e -> { ScreenEvents.BeforeRender r = per(e.getScreen(), "beforeRender"); if (r != null) r.beforeRender(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY(), e.getPartialTick()); });
        bus.addListener(ScreenEvent.Render.Post.class, e -> { ScreenEvents.AfterRender r = per(e.getScreen(), "afterRender"); if (r != null) r.afterRender(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY(), e.getPartialTick()); });
        bus.addListener(ClientTickEvent.Pre.class, e -> {
            if (!started) { started = true; ClientLifecycleEvents.CLIENT_STARTED.invoker().onClientStarted(mc); }
            if (mc.screen != null) { ScreenEvents.BeforeTick r = per(mc.screen, "beforeTick"); if (r != null) r.beforeTick(mc.screen); }
        });
        bus.addListener(ClientTickEvent.Post.class, e -> { if (mc.screen != null) { ScreenEvents.AfterTick r = per(mc.screen, "afterTick"); if (r != null) r.afterTick(mc.screen); } });
        bus.addListener(GameShuttingDownEvent.class, e -> ClientLifecycleEvents.CLIENT_STOPPING.invoker().onClientStopping(mc));

        bus.addListener(ScreenEvent.MouseButtonPressed.Pre.class, e -> {
            Screen s = e.getScreen();
            ScreenMouseEvents.AllowMouseClick a = per(s, "allowMouseClick");
            if (a != null && !a.allowMouseClick(s, e.getMouseX(), e.getMouseY(), e.getButton())) { e.setCanceled(true); return; }
            ScreenMouseEvents.BeforeMouseClick b = per(s, "beforeMouseClick");
            if (b != null) b.beforeMouseClick(s, e.getMouseX(), e.getMouseY(), e.getButton());
        });
        bus.addListener(ScreenEvent.MouseButtonPressed.Post.class, e -> { ScreenMouseEvents.AfterMouseClick r = per(e.getScreen(), "afterMouseClick"); if (r != null) r.afterMouseClick(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getButton()); });
        bus.addListener(ScreenEvent.MouseButtonReleased.Pre.class, e -> {
            Screen s = e.getScreen();
            ScreenMouseEvents.AllowMouseRelease a = per(s, "allowMouseRelease");
            if (a != null && !a.allowMouseRelease(s, e.getMouseX(), e.getMouseY(), e.getButton())) { e.setCanceled(true); return; }
            ScreenMouseEvents.BeforeMouseRelease b = per(s, "beforeMouseRelease");
            if (b != null) b.beforeMouseRelease(s, e.getMouseX(), e.getMouseY(), e.getButton());
        });
        bus.addListener(ScreenEvent.MouseButtonReleased.Post.class, e -> { ScreenMouseEvents.AfterMouseRelease r = per(e.getScreen(), "afterMouseRelease"); if (r != null) r.afterMouseRelease(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getButton()); });
        bus.addListener(ScreenEvent.MouseScrolled.Pre.class, e -> {
            Screen s = e.getScreen();
            ScreenMouseEvents.AllowMouseScroll a = per(s, "allowMouseScroll");
            if (a != null && !a.allowMouseScroll(s, e.getMouseX(), e.getMouseY(), e.getScrollDeltaX(), e.getScrollDeltaY())) { e.setCanceled(true); return; }
            ScreenMouseEvents.BeforeMouseScroll b = per(s, "beforeMouseScroll");
            if (b != null) b.beforeMouseScroll(s, e.getMouseX(), e.getMouseY(), e.getScrollDeltaX(), e.getScrollDeltaY());
        });
        bus.addListener(ScreenEvent.MouseScrolled.Post.class, e -> { ScreenMouseEvents.AfterMouseScroll r = per(e.getScreen(), "afterMouseScroll"); if (r != null) r.afterMouseScroll(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getScrollDeltaX(), e.getScrollDeltaY()); });
        bus.addListener(ScreenEvent.KeyPressed.Pre.class, e -> {
            Screen s = e.getScreen();
            ScreenKeyboardEvents.AllowKeyPress a = per(s, "allowKeyPress");
            if (a != null && !a.allowKeyPress(s, e.getKeyCode(), e.getScanCode(), e.getModifiers())) { e.setCanceled(true); return; }
            ScreenKeyboardEvents.BeforeKeyPress b = per(s, "beforeKeyPress");
            if (b != null) b.beforeKeyPress(s, e.getKeyCode(), e.getScanCode(), e.getModifiers());
        });
        bus.addListener(ScreenEvent.KeyPressed.Post.class, e -> { ScreenKeyboardEvents.AfterKeyPress r = per(e.getScreen(), "afterKeyPress"); if (r != null) r.afterKeyPress(e.getScreen(), e.getKeyCode(), e.getScanCode(), e.getModifiers()); });
        bus.addListener(ScreenEvent.KeyReleased.Pre.class, e -> {
            Screen s = e.getScreen();
            ScreenKeyboardEvents.AllowKeyRelease a = per(s, "allowKeyRelease");
            if (a != null && !a.allowKeyRelease(s, e.getKeyCode(), e.getScanCode(), e.getModifiers())) { e.setCanceled(true); return; }
            ScreenKeyboardEvents.BeforeKeyRelease b = per(s, "beforeKeyRelease");
            if (b != null) b.beforeKeyRelease(s, e.getKeyCode(), e.getScanCode(), e.getModifiers());
        });
        bus.addListener(ScreenEvent.KeyReleased.Post.class, e -> { ScreenKeyboardEvents.AfterKeyRelease r = per(e.getScreen(), "afterKeyRelease"); if (r != null) r.afterKeyRelease(e.getScreen(), e.getKeyCode(), e.getScanCode(), e.getModifiers()); });

        // Client commands: NeoForge's client command source doubles as Fabric's (see ClientCommandSourceStackMixin).
        bus.addListener(RegisterClientCommandsEvent.class, e ->
                ClientCommandRegistrationCallback.EVENT.invoker().register((CommandDispatcher) e.getDispatcher(), e.getBuildContext()));
    }
}
