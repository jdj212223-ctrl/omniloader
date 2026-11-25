package dev.omniloader.runtime.fabricapi;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Client-side Fabric API events. Only loaded on the client. */
public final class ClientBridge {
    private static final Logger LOG = LoggerFactory.getLogger("Omniloader");
    private static final List<KeyMapping> pendingKeys = new ArrayList<>();
    private static boolean keysRegistered;

    private ClientBridge() {}

    public static void init(IEventBus modBus) {

        modBus.addListener(RegisterKeyMappingsEvent.class, e -> {
            synchronized (pendingKeys) {
                pendingKeys.forEach(e::register);
                keysRegistered = true;
            }
        });
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Pre.class, e -> ClientTickEvents.START_CLIENT_TICK.invoker().onStartTick(Minecraft.getInstance()));
        bus.addListener(ClientTickEvent.Post.class, e -> ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(Minecraft.getInstance()));
        bus.addListener(LevelTickEvent.Pre.class, e -> { if (e.getLevel() instanceof ClientLevel l) ClientTickEvents.START_WORLD_TICK.invoker().onStartTick(l); });
        bus.addListener(LevelTickEvent.Post.class, e -> { if (e.getLevel() instanceof ClientLevel l) ClientTickEvents.END_WORLD_TICK.invoker().onEndTick(l); });
    }

    public static KeyMapping registerKey(KeyMapping key) {
        synchronized (pendingKeys) {
            if (!keysRegistered) { pendingKeys.add(key); return key; }
        }
        // Registered late (after NeoForge's key event): add it to the options list directly, like Fabric does.
        try {
            var options = Minecraft.getInstance().options;
            Field f = options.getClass().getDeclaredField("keyMappings");
            f.setAccessible(true);
            f.set(options, ArrayUtils.add((KeyMapping[]) f.get(options), key));
        } catch (ReflectiveOperationException ex) {
            LOG.warn("[Omniloader] Could not add late key binding {}", key.getName(), ex);
        }
        return key;
    }
}
