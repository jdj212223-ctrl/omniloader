package org.quiltmc.loader.api.entrypoint;

import org.quiltmc.loader.api.ModContainer;

@FunctionalInterface
public interface PreLaunchEntrypoint { void onPreLaunch(ModContainer mod); }
