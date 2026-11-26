package net.neoforged.neoforgespi.locating;
import net.neoforged.neoforgespi.language.*;
import java.nio.file.Path; import java.util.*; import java.util.function.Supplier;
public interface IModFile {
    Path findResource(String... pathName);
    Supplier<Map<String, Object>> getSubstitutionMap();
    Type getType();
    Path getFilePath();
    cpw.mods.jarhandling.SecureJar getSecureJar();
    List<IModInfo> getModInfos();
    ModFileScanData getScanResult();
    String getFileName();
    ModFileDiscoveryAttributes getDiscoveryAttributes();
    IModFileInfo getModFileInfo();
    enum Type { MOD, LIBRARY, GAMELIBRARY }
}
