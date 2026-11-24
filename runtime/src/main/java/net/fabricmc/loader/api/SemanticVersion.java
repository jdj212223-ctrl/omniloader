package net.fabricmc.loader.api;

import dev.omniloader.runtime.fabric.VersionImpl;

import java.util.Optional;

public interface SemanticVersion extends Version {
    int COMPONENT_WILDCARD = Integer.MIN_VALUE;

    int getVersionComponentCount();
    int getVersionComponent(int pos);
    Optional<String> getPrereleaseKey();
    Optional<String> getBuildKey();
    boolean hasWildcard();

    @Deprecated
    default int compareTo(SemanticVersion o) { return compareTo((Version) o); }

    static SemanticVersion parse(String s) throws VersionParsingException {
        Version v = VersionImpl.parse(s, true);
        if (v instanceof SemanticVersion sv) return sv;
        throw new VersionParsingException("Not a semantic version: " + s);
    }
}
