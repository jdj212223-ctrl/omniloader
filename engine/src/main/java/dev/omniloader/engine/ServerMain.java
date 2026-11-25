package dev.omniloader.engine;

/** Start a Minecraft server on Omniloader's own engine. */
public final class ServerMain {
    public static void main(String[] args) throws Throwable { Engine.launch(false, args, "net.minecraft.server.Main"); }
}
