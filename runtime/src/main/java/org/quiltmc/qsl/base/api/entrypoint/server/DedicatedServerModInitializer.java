package org.quiltmc.qsl.base.api.entrypoint.server;

import org.quiltmc.loader.api.ModContainer;

/** Quilt "server_init" entrypoint. */
@FunctionalInterface
public interface DedicatedServerModInitializer { void onInitializeServer(ModContainer mod); }
