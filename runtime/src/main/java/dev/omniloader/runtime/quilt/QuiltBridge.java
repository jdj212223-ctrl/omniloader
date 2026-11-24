package dev.omniloader.runtime.quilt;

import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.ModMetadata;
import org.quiltmc.loader.api.Version;

import java.nio.file.Path;

/** Shows a Fabric-side mod container through the Quilt API. */
public final class QuiltBridge {
    private QuiltBridge() {}

    public static ModContainer wrap(net.fabricmc.loader.api.ModContainer f) {
        var fm = f.getMetadata();
        Version v = () -> fm.getVersion().getFriendlyString();
        ModMetadata meta = new ModMetadata() {
            public String id() { return fm.getId(); }
            public String group() { return "loader.fabric"; }
            public Version version() { return v; }
            public String name() { return fm.getName(); }
            public String description() { return fm.getDescription(); }
        };
        return new ModContainer() {
            public ModMetadata metadata() { return meta; }
            public Path rootPath() { return f.getRootPaths().isEmpty() ? Path.of(".") : f.getRootPaths().get(0); }
        };
    }
}
