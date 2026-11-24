package org.quiltmc.loader.api.minecraft;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public final class MinecraftQuiltLoader {
    private MinecraftQuiltLoader() {}
    public static EnvType getEnvironmentType() { return FabricLoader.getInstance().getEnvironmentType(); }
}
