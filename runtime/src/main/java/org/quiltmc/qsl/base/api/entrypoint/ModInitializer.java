package org.quiltmc.qsl.base.api.entrypoint;

import org.quiltmc.loader.api.ModContainer;

/** Quilt "init" entrypoint. */
@FunctionalInterface
public interface ModInitializer { void onInitialize(ModContainer mod); }
