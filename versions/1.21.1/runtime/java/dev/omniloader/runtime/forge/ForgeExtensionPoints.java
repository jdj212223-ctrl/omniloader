package dev.omniloader.runtime.forge;

import net.neoforged.fml.IExtensionPoint;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.function.Supplier;

/** Forge mods' extension points, translated to NeoForge's where one exists. */
public final class ForgeExtensionPoints {
    private ForgeExtensionPoints() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(ModContainer container, Class<? extends IExtensionPoint> point, Supplier<? extends IExtensionPoint> extension) {
        if (point == net.minecraftforge.fml.IExtensionPoint$DisplayTest.class) return; // NeoForge doesn't use display tests
        if (point == net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class) {
            if (FMLEnvironment.dist.isClient()) Client.configScreen(container, (Supplier) extension);
            return;
        }
        container.registerExtensionPoint((Class) point, (Supplier) extension);
    }

    static final class Client {
        static void configScreen(ModContainer container, Supplier<net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory> f) {
            container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                    (net.neoforged.neoforge.client.gui.IConfigScreenFactory) (mod, parent) -> f.get().screenFunction().apply(net.minecraft.client.Minecraft.getInstance(), parent));
        }
    }
}
