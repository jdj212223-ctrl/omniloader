package cpw.mods.jarhandling;
import java.nio.file.Path;
/** The bit of a mod jar NeoForge asks for: its contents as a folder. */
public interface SecureJar {
    Path getRootPath();
    Path getPrimaryPath();
    String name();
    default Path getPath(String first, String... rest) { return getRootPath().resolve(first.startsWith("/") ? first.substring(1) : first).resolve(String.join("/", rest)); }
    enum Status { NONE, INVALID, UNVERIFIED, VERIFIED }
}
