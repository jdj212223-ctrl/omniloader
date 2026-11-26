package net.neoforged.fml;
import org.apache.maven.artifact.versioning.ComparableVersion; import java.util.Map;
/** Omniloader doesn't phone home: every mod's update check simply says "unknown". */
public class VersionChecker {
    private static final CheckResult NONE = new CheckResult(Status.FAILED, null, Map.of(), null);
    public static void startVersionCheck() {}
    public static CheckResult getResult(net.neoforged.neoforgespi.language.IModInfo mod) { return NONE; }
    public enum Status {
        PENDING(), FAILED(), UP_TO_DATE(), OUTDATED(3, true), AHEAD(), BETA(), BETA_OUTDATED(6, true);
        private final int sheetOffset; private final boolean draw, animated;
        Status() { this(0, false, false); }
        Status(int so, boolean ani) { this(so, true, ani); }
        Status(int so, boolean draw, boolean ani) { sheetOffset = so; this.draw = draw; animated = ani; }
        public int getSheetOffset() { return sheetOffset; }
        public boolean shouldDraw() { return draw; }
        public boolean isAnimated() { return animated; }
    }
    public record CheckResult(Status status, ComparableVersion target, Map<ComparableVersion, String> changes, String url) {}
}
