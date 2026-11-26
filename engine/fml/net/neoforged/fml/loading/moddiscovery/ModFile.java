package net.neoforged.fml.loading.moddiscovery;
import cpw.mods.jarhandling.SecureJar;
import net.neoforged.neoforgespi.language.*;
import net.neoforged.neoforgespi.locating.*;
import java.nio.file.*; import java.util.*; import java.util.function.Supplier;
/** A mod jar (or library) as Omniloader loaded it. */
public class ModFile implements IModFile {
    private final Path file;
    private final Path root;
    private final Type type;
    private final ModFileDiscoveryAttributes attributes;
    private final Map<String, Object> substitutions;
    private ModFileInfo info;
    private ModFileScanData scan = new ModFileScanData();
    private final SecureJar jar;

    public ModFile(Path file, Path root, Type type, ModFileDiscoveryAttributes attributes, Map<String, Object> substitutions) {
        this.file = file; this.root = root; this.type = type; this.attributes = attributes; this.substitutions = substitutions;
        this.jar = new SecureJar() {
            public Path getRootPath() { return root; }
            public Path getPrimaryPath() { return file; }
            public String name() { return info != null ? info.moduleName() : file.getFileName().toString(); }
        };
    }

    public void setInfo(ModFileInfo info) { this.info = info; }
    public void setScanResult(ModFileScanData scan) { this.scan = scan; scan.addModFileInfo(info); }
    public Path root() { return root; }

    @Override public Path findResource(String... path) {
        Path p = root;
        for (String s : path) for (String part : s.split("/")) if (!part.isEmpty()) p = p.resolve(part);
        return p;
    }
    @Override public Supplier<Map<String, Object>> getSubstitutionMap() { return () -> substitutions; }
    @Override public Type getType() { return type; }
    @Override public Path getFilePath() { return file; }
    @Override public SecureJar getSecureJar() { return jar; }
    @Override public List<IModInfo> getModInfos() { return info == null ? List.of() : info.getMods(); }
    @Override public ModFileScanData getScanResult() { return scan; }
    @Override public String getFileName() { return file.getFileName().toString(); }
    @Override public ModFileDiscoveryAttributes getDiscoveryAttributes() { return attributes; }
    @Override public ModFileInfo getModFileInfo() { return info; }
    @Override public String toString() { return getFileName(); }
}
