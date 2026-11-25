package dev.omniloader.loader.convert;

/**
 * Java version labels inside class files. A mod built for a newer Java has a higher number,
 * and an older Java refuses to load it even if the code would run fine. We lower the label;
 * the startup check then makes sure the mod doesn't use Java features that are really missing.
 */
public final class ClassVersions {
    private ClassVersions() {}

    public static int major(byte[] c) {
        return c.length < 8 ? 0 : ((c[6] & 0xff) << 8) | (c[7] & 0xff);
    }

    /** @return true if the label was lowered */
    public static boolean downgrade(byte[] c, int max) {
        if (major(c) <= max) return false;
        c[6] = (byte) (max >> 8);
        c[7] = (byte) max;
        return true;
    }

    /** Java feature version for a class-file version (65 = Java 21). */
    public static int java(int major) { return major - 44; }
}
