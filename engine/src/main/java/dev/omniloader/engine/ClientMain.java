package dev.omniloader.engine;

/** Start Minecraft (client) on Omniloader's own engine. Use as the launcher's main class. */
public final class ClientMain {
    public static void main(String[] args) throws Throwable { Engine.launch(true, args, "net.minecraft.client.main.Main"); }
}
