package net.fabricmc.api;

/** Fabric "client" entrypoint. */
@FunctionalInterface
public interface ClientModInitializer { void onInitializeClient(); }
