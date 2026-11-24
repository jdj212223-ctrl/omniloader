package org.quiltmc.loader.api;

/** Quilt version (Omniloader stand-in). */
public interface Version extends Comparable<Version> {
    String raw();
    default int compareTo(Version o) { return raw().compareTo(o.raw()); }
}
