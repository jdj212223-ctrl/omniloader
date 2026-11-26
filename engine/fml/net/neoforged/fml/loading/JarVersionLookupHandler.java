package net.neoforged.fml.loading;
import java.util.Optional;
public class JarVersionLookupHandler {
    public static Optional<String> getVersion(Class<?> c) {
        String v = c.getPackage() == null ? null : c.getPackage().getImplementationVersion();
        if (v == null && c.getName().startsWith("net.neoforged.neoforge.")) v = FMLLoader.versionInfo().neoForgeVersion();
        return Optional.ofNullable(v);
    }
}
