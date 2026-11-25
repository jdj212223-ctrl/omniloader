package dev.omniloader.loader.convert;

/** Answers "does this mod say it supports this Minecraft version?" for Fabric and NeoForge style ranges. */
public final class VersionRanges {
    private VersionRanges() {}

    /** NeoForge/Maven range like "[1.21.1,1.22)". Unparseable ranges count as "yes". */
    public static boolean mavenAccepts(String range, String version) {
        if (range == null || range.isBlank() || range.equals("*")) return true;
        try {
            // engine copy: simple Maven range parser (no Maven library at runtime), e.g. "[1.21,1.22)" or "[1.21.1]" or "1.21.1"
            for (String part : range.split("(?<=[\\])]),")) {
                part = part.trim();
                if (part.isEmpty()) continue;
                if (!part.startsWith("[") && !part.startsWith("(")) { if (cmp(version, part) >= 0) return true; continue; }
                boolean loIn = part.startsWith("["), hiIn = part.endsWith("]");
                String body = part.substring(1, part.length() - 1);
                if (!body.contains(",")) { if (cmp(version, body.trim()) == 0) return true; continue; }
                String lo = body.substring(0, body.indexOf(',')).trim(), hi = body.substring(body.indexOf(',') + 1).trim();
                boolean ok = lo.isEmpty() || (loIn ? cmp(version, lo) >= 0 : cmp(version, lo) > 0);
                ok &= hi.isEmpty() || (hiIn ? cmp(version, hi) <= 0 : cmp(version, hi) < 0);
                if (ok) return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /** Fabric predicate(s) like ">=1.21 <1.22", "1.21.x", "~1.21.1", "^1.21", alternatives separated by " or ". */
    public static boolean fabricAccepts(String predicates, String version) {
        if (predicates == null || predicates.isBlank()) return true;
        for (String alt : predicates.split(" or ")) if (all(alt.trim(), version)) return true;
        return false;
    }

    private static boolean all(String alt, String v) {
        if (alt.isEmpty() || alt.equals("*")) return true;
        for (String c : alt.split("\\s+")) if (!one(c, v)) return false;
        return true;
    }

    private static boolean one(String c, String v) {
        try {
            if (c.startsWith(">=")) return cmp(v, c.substring(2)) >= 0;
            if (c.startsWith("<=")) return cmp(v, c.substring(2)) <= 0;
            if (c.startsWith(">")) return cmp(v, c.substring(1)) > 0;
            if (c.startsWith("<")) return cmp(v, c.substring(1)) < 0;
            if (c.startsWith("=")) c = c.substring(1);
            if (c.startsWith("~")) { String b = c.substring(1); return cmp(v, b) >= 0 && prefix(v, b, 2); }
            if (c.startsWith("^")) { String b = c.substring(1); return cmp(v, b) >= 0 && prefix(v, b, 1); }
            if (c.endsWith(".x") || c.endsWith(".X") || c.endsWith(".*")) return (v + ".").startsWith(c.substring(0, c.length() - 1));
            return cmp(v, c) == 0;
        } catch (RuntimeException e) {
            return true; // something we don't understand: don't block the mod over it
        }
    }

    private static boolean prefix(String v, String base, int parts) {
        String[] a = clean(v).split("\\."), b = clean(base).split("\\.");
        for (int i = 0; i < parts && i < b.length; i++) if (i >= a.length || !a[i].equals(b[i])) return false;
        return true;
    }

    private static String clean(String s) {
        int i = s.indexOf('-'); if (i >= 0) s = s.substring(0, i);
        i = s.indexOf('+'); if (i >= 0) s = s.substring(0, i);
        return s;
    }

    /** Compares dotted version numbers (1.21.1 vs 1.21). Pre-release tags are ignored. */
    static int cmp(String a, String b) {
        String[] x = clean(a).split("\\."), y = clean(b).split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? Integer.parseInt(x[i]) : 0, q = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }
}
