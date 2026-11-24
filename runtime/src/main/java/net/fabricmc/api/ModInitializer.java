package net.fabricmc.api;

/** Fabric "main" entrypoint: runs on both client and server while the game starts. */
@FunctionalInterface
public interface ModInitializer { void onInitialize(); }
