package com.terraformersmc.modmenu.api;

/** Mod Menu's API (provided by Omniloader). Omniloader doesn't check for updates. */
public interface UpdateChecker {
    UpdateInfo checkForUpdates();
}
