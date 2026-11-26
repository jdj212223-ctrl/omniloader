package dev.omniloader.runtime;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.omniloader.runtime.fabric.FabricLoaderImpl;
import dev.omniloader.runtime.fabric.ModContainerImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.ModListScreen;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * One mod menu for every kind of mod (NeoForge's mod list):
 *  - Fabric mods' settings screens (their Mod Menu "modmenu" entrypoint) get a Config button,
 *  - NeoForge/Forge mods that have settings but no screen get NeoForge's automatic settings screen.
 * (The "Mods" button itself is NeoForge's: on the title screen and in the pause menu.)
 */
public final class ModMenuBridge {
    private static final Logger LOG = LoggerFactory.getLogger("Omniloader");
    private ModMenuBridge() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(FMLLoadCompleteEvent.class, e -> e.enqueueWork(ModMenuBridge::install));
    }

    private static void install() {
        int fabric = 0, auto = 0;
        FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;
        for (FabricLoaderImpl.Entry e : loader.rawEntrypoints("modmenu")) {
            try {
                ModMenuApi api = (ModMenuApi) loader.instantiate(e, ModMenuApi.class);
                if (attach(e.mod().neoId, api.getModConfigScreenFactory())) fabric++;
                for (Map.Entry<String, ConfigScreenFactory<?>> p : api.getProvidedConfigScreenFactories().entrySet()) {
                    String id = loader.getModContainer(p.getKey()).map(c -> ((ModContainerImpl) c).neoId).orElse(p.getKey());
                    if (attach(id, p.getValue())) fabric++;
                }
            } catch (Throwable t) {
                LOG.warn("[Omniloader] {}'s settings screen could not be added to the mod menu: {}", e.mod().getMetadata().getName(), t.toString());
            }
        }
        // NeoForge and Forge mods with settings files but no settings screen: use NeoForge's ready-made one
        for (ModContainer c : ModList.get().getSortedMods()) {
            if (c.getCustomExtension(IConfigScreenFactory.class).isPresent() || ModConfigs.getModConfigs(c.getModId()).isEmpty()) continue;
            if (c.getModId().equals("neoforge")) continue;
            c.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
            auto++;
        }
        LOG.info("[Omniloader] Mod menu: {} Fabric settings screen(s) linked, {} automatic settings screen(s) added", fabric, auto);
        if (Boolean.getBoolean("omniloader.testModMenu")) selfTest();
    }

    /** Test switch: opens every mod's settings screen once (without showing it) and logs the result. */
    private static void selfTest() {
        Screen parent = new net.minecraft.client.gui.screens.TitleScreen();
        for (ModContainer c : ModList.get().getSortedMods()) {
            var f = c.getCustomExtension(IConfigScreenFactory.class);
            if (f.isEmpty()) continue;
            try {
                Screen s = f.get().createScreen(c, parent);
                LOG.info("[Omniloader] MODMENU-TEST {}: {}", c.getModId(), s == null ? "no screen" : s.getClass().getName());
            } catch (Throwable t) {
                LOG.warn("[Omniloader] MODMENU-TEST {}: FAILED {}", c.getModId(), t.toString());
            }
        }
    }

    private static boolean attach(String neoId, ConfigScreenFactory<?> factory) {
        if (factory == null) return false;
        ModContainer c = ModList.get().getModContainerById(neoId).orElse(null);
        if (c == null || c.getCustomExtension(IConfigScreenFactory.class).isPresent()) return false;
        c.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) (mod, parent) -> {
            try {
                Screen s = factory.create(parent);
                return s != null ? s : parent;   // some mods only have settings in certain situations
            } catch (LinkageError | RuntimeException t) {
                // usually: the settings screen is built with another mod (like Cloth Config) that isn't installed
                LOG.warn("[Omniloader] {}'s settings screen can't open: {}", neoId, t.toString());
                net.minecraft.client.gui.components.toasts.SystemToast.addOrUpdate(Minecraft.getInstance().getToasts(),
                        net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PACK_LOAD_FAILURE,
                        Component.literal("Settings unavailable"), Component.literal(missingHint(t)));
                return parent;
            }
        });
        return true;
    }

    private static String missingHint(Throwable t) {
        String m = String.valueOf(t.getMessage());
        if (m.contains("clothconfig")) return "This mod's settings need Cloth Config";
        if (m.contains("yacl") || m.contains("isxander")) return "This mod's settings need YetAnotherConfigLib";
        return "This mod's settings screen needs another mod";
    }
}
