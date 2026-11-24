package net.fabricmc.loader.api.metadata;

import net.fabricmc.loader.api.Version;

public interface ModDependency {
    Kind getKind();
    String getModId();
    boolean matches(Version version);

    enum Kind {
        DEPENDS("depends", true, false), RECOMMENDS("recommends", true, true), SUGGESTS("suggests", true, true),
        CONFLICTS("conflicts", false, true), BREAKS("breaks", false, false);
        private final String key; private final boolean positive, soft;
        Kind(String key, boolean positive, boolean soft) { this.key = key; this.positive = positive; this.soft = soft; }
        public String getKey() { return key; }
        public boolean isPositive() { return positive; }
        public boolean isSoft() { return soft; }
        public static Kind parse(String key) { for (Kind k : values()) if (k.key.equals(key)) return k; return null; }
    }
}
