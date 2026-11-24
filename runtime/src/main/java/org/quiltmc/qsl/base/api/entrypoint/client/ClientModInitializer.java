package org.quiltmc.qsl.base.api.entrypoint.client;

import org.quiltmc.loader.api.ModContainer;

/** Quilt "client_init" entrypoint. */
@FunctionalInterface
public interface ClientModInitializer { void onInitializeClient(ModContainer mod); }
