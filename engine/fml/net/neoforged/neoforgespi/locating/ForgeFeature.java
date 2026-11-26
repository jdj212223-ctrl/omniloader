package net.neoforged.neoforgespi.locating;
public final class ForgeFeature {
    private ForgeFeature() {}
    public record Bound(String featureName, String featureBound, net.neoforged.neoforgespi.language.IModInfo modInfo) {}
    public static void registerFeature(String name, Object test) {}
}
