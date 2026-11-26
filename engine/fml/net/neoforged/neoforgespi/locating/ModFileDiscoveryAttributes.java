package net.neoforged.neoforgespi.locating;
public record ModFileDiscoveryAttributes(IModFile parent, Object reader, Object locator, Object dependencyLocator) {
    public static final ModFileDiscoveryAttributes DEFAULT = new ModFileDiscoveryAttributes(null, null, null, null);
    public ModFileDiscoveryAttributes withParent(IModFile p) { return new ModFileDiscoveryAttributes(p, reader, locator, dependencyLocator); }
}
