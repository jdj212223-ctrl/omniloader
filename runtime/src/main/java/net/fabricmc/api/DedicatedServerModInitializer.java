package net.fabricmc.api;

/** Fabric "server" entrypoint (dedicated servers only). */
@FunctionalInterface
public interface DedicatedServerModInitializer { void onInitializeServer(); }
