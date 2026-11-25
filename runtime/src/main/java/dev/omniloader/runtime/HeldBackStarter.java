package dev.omniloader.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.omniloader.runtime.fabric.FabricLoaderImpl;
import dev.omniloader.runtime.fabric.ModContainerImpl;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

/**
 * Starts NeoForge mods that Omniloader adapted from another Minecraft version or a newer Java,
 * but only those that passed the "does everything it uses exist here?" check.
 * Does what NeoForge would have done: builds the @Mod class and hooks up @EventBusSubscriber classes.
 */
public final class HeldBackStarter {
    private static final Logger LOG = LoggerFactory.getLogger("Omniloader");

    private HeldBackStarter() {}

    public static void startAll() {
        Dist dist = Platform.isClient() ? Dist.CLIENT : Dist.DEDICATED_SERVER;
        if (FabricLoaderImpl.INSTANCE.heldBack.keySet().stream().anyMatch(c -> c.forge && c.active)) Platform.initForge();
        var loading = net.neoforged.fml.ModLoadingContext.get();
        ModContainer previous = loading.getActiveContainer();
        for (var e : FabricLoaderImpl.INSTANCE.heldBack.entrySet()) {
            ModContainerImpl c = e.getKey();
            String name = c.getMetadata().getName();
            if (!c.active) {
                LOG.warn("[Omniloader] Not starting {}: {}", name, c.offReason);
                continue;
            }
            ModContainer container = ModList.get().getModContainerById(c.neoId).orElse(null);
            if (container == null) continue;
            IEventBus modBus = container.getEventBus();
            ClassLoader cl = HeldBackStarter.class.getClassLoader();
            loading.setActiveContainer(container); // what the mod sees as "the mod being loaded right now"
            try {
                for (JsonElement s : e.getValue().getAsJsonArray("subscribers")) {
                    JsonObject o = s.getAsJsonObject();
                    if (!forDist(o, dist)) continue;
                    if (o.has("modid") && !o.get("modid").getAsString().equals(c.neoId)) continue;
                    subscribe(Class.forName(o.get("class").getAsString(), true, cl), modBus);
                }
                for (JsonElement s : e.getValue().getAsJsonArray("entries")) {
                    JsonObject o = s.getAsJsonObject();
                    if (!forDist(o, dist)) continue;
                    construct(Class.forName(o.get("class").getAsString(), true, cl), modBus, container, dist);
                }
                LOG.info("[Omniloader] Started {} ({})", name, c.forge ? "Forge mod, translated to NeoForge"
                        : "made for " + (c.crossVersion ? "Minecraft " + c.builtFor : "Java " + c.builtForJava));
            } catch (Throwable t) {
                // One adapted mod failing to start must not take the whole game down.
                Throwable root = t;
                while (root.getCause() != null) root = root.getCause();
                c.active = false;
                c.offReason = "it failed while starting: " + root
                        + (String.valueOf(root).contains("invalid dist") ? " (it's a client-only mod; it isn't needed on a server)" : "");
                LOG.error("[Omniloader] {} failed while starting and was switched off: {}", name, c.offReason, t);
                FabricLoaderImpl.INSTANCE.noteSwitchedOff(c);
            } finally {
                loading.setActiveContainer(previous);
            }
        }
    }

    private static boolean forDist(JsonObject o, Dist dist) {
        var arr = o.getAsJsonArray("dist");
        if (arr == null || arr.isEmpty()) return true;
        for (JsonElement d : arr) if (d.getAsString().equals(dist.name())) return true;
        return false;
    }

    private static void construct(Class<?> cls, IEventBus bus, ModContainer container, Dist dist) throws Exception {
        Constructor<?> best = null;
        for (Constructor<?> k : cls.getDeclaredConstructors()) if (best == null || k.getParameterCount() > best.getParameterCount()) best = k;
        Object[] args = new Object[best.getParameterCount()];
        Class<?>[] types = best.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (types[i].isAssignableFrom(bus.getClass()) || types[i] == IEventBus.class) args[i] = bus;
            else if (types[i].isInstance(container)) args[i] = container;
            else if (types[i] == Dist.class) args[i] = dist;
            else if (types[i].getName().equals("net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext"))
                args[i] = types[i].getConstructor(ModContainer.class).newInstance(container); // Forge mods ask for their loading context
            else throw new IllegalStateException("don't know how to provide " + types[i].getName() + " to " + cls.getName());
        }
        best.setAccessible(true);
        best.newInstance(args);
    }

    /** Registers a class's static @SubscribeEvent methods, each on the bus its event belongs to. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void subscribe(Class<?> cls, IEventBus modBus) {
        for (Method m : cls.getDeclaredMethods()) {
            SubscribeEvent a = m.getAnnotation(SubscribeEvent.class);
            if (a == null || !Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1) continue;
            Class<?> type = m.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(type)) continue;
            m.setAccessible(true);
            IEventBus bus = IModBusEvent.class.isAssignableFrom(type) ? modBus : NeoForge.EVENT_BUS;
            Consumer listener = ev -> {
                try { m.invoke(null, ev); }
                catch (ReflectiveOperationException ex) { throw new RuntimeException(ex.getCause() != null ? ex.getCause() : ex); }
            };
            bus.addListener(a.priority(), a.receiveCanceled(), (Class) type, listener);
        }
    }
}
