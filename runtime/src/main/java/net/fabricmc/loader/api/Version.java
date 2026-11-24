package net.fabricmc.loader.api;

import dev.omniloader.runtime.fabric.VersionImpl;

public interface Version extends Comparable<Version> {
    String getFriendlyString();

    static Version parse(String string) throws VersionParsingException { return VersionImpl.parse(string, false); }
}
