package dev.omniloader.runtime.fabric;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/** Fabric-style versions: "1.2.3-beta.1+build.5", or anything else as a plain string. */
public final class VersionImpl {
    private VersionImpl() {}
    private static final Pattern DOT_SEP = Pattern.compile("\\.");

    public static Version parse(String s, boolean strict) throws VersionParsingException {
        try {
            return Semantic.parse(s);
        } catch (VersionParsingException e) {
            if (strict) throw e;
            if (s == null || s.isEmpty()) throw new VersionParsingException("empty version");
            return new Plain(s);
        }
    }

    public static Version parseOrPlain(String s) {
        try { return parse(s, false); } catch (VersionParsingException e) { return new Plain(String.valueOf(s)); }
    }

    public record Plain(String s) implements Version {
        public String getFriendlyString() { return s; }
        public int compareTo(Version o) { return s.compareTo(o.getFriendlyString()); }
        public String toString() { return s; }
    }

    public static final class Semantic implements SemanticVersion {
        private final int[] components;
        private final String pre, build, friendly;

        private Semantic(int[] c, String pre, String build, String friendly) {
            this.components = c; this.pre = pre; this.build = build; this.friendly = friendly;
        }

        static Semantic parse(String s) throws VersionParsingException {
            if (s == null || s.isEmpty()) throw new VersionParsingException("empty");
            String core = s, pre = null, build = null;
            int plus = core.indexOf('+');
            if (plus >= 0) { build = core.substring(plus + 1); core = core.substring(0, plus); }
            int dash = core.indexOf('-');
            if (dash >= 0) { pre = core.substring(dash + 1); core = core.substring(0, dash); }
            String[] parts = DOT_SEP.split(core);
            if (parts.length == 0) throw new VersionParsingException("no numbers in " + s);
            int[] c = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                String p = parts[i];
                if (p.equals("x") || p.equals("X") || p.equals("*")) { c[i] = COMPONENT_WILDCARD; continue; }
                try { c[i] = Integer.parseInt(p); if (c[i] < 0) throw new NumberFormatException(); }
                catch (NumberFormatException e) { throw new VersionParsingException("bad number '" + p + "' in " + s); }
            }
            return new Semantic(c, pre, build, s);
        }

        public int getVersionComponentCount() { return components.length; }
        public int getVersionComponent(int pos) { return pos < components.length ? components[pos] : 0; }
        public Optional<String> getPrereleaseKey() { return Optional.ofNullable(pre); }
        public Optional<String> getBuildKey() { return Optional.ofNullable(build); }
        public boolean hasWildcard() { return Arrays.stream(components).anyMatch(x -> x == COMPONENT_WILDCARD); }
        public String getFriendlyString() { return friendly; }
        public String toString() { return friendly; }

        public int compareTo(Version other) {
            if (!(other instanceof SemanticVersion o)) return friendly.compareTo(other.getFriendlyString());
            int n = Math.max(getVersionComponentCount(), o.getVersionComponentCount());
            for (int i = 0; i < n; i++) {
                int a = getVersionComponent(i), b = o.getVersionComponent(i);
                if (a == COMPONENT_WILDCARD || b == COMPONENT_WILDCARD) break;
                if (a != b) return Integer.compare(a, b);
            }
            Optional<String> op = o.getPrereleaseKey();
            if (pre == null) return op.isPresent() ? 1 : 0;
            if (op.isEmpty()) return -1;
            String[] x = DOT_SEP.split(pre), y = DOT_SEP.split(op.get());
            for (int i = 0; i < Math.min(x.length, y.length); i++) {
                boolean xn = x[i].matches("\\d+"), yn = y[i].matches("\\d+");
                int r = xn && yn ? Long.compare(Long.parseLong(x[i]), Long.parseLong(y[i])) : xn ? -1 : yn ? 1 : x[i].compareTo(y[i]);
                if (r != 0) return r;
            }
            return Integer.compare(x.length, y.length);
        }

        @Override public boolean equals(Object o) { return o instanceof Semantic s && compareTo(s) == 0 && friendly.equals(s.friendly); }
        @Override public int hashCode() { return friendly.hashCode(); }
    }
}
