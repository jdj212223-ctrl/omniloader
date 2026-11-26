package com.terraformersmc.modmenu.api;

public interface UpdateInfo {
    boolean isUpdateAvailable();
    String getDownloadLink();
    default net.minecraft.network.chat.Component getUpdateMessage() { return null; }
    default UpdateChannel getUpdateChannel() { return UpdateChannel.RELEASE; }
}
