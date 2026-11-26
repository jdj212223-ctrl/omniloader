package net.neoforged.fml;
import net.neoforged.neoforgespi.language.IModInfo; import net.neoforged.neoforgespi.locating.IModFile;
import java.nio.file.Path; import java.util.List;
public record ModLoadingIssue(Severity severity, String translationKey, List<Object> translationArgs, Throwable cause, Path affectedPath, IModFile affectedModFile, IModInfo affectedMod) {
    public ModLoadingIssue(Severity severity, String translationKey, List<Object> translationArgs) { this(severity, translationKey, translationArgs, null, null, null, null); }
    public static ModLoadingIssue error(String key, Object... args) { return new ModLoadingIssue(Severity.ERROR, key, List.of(args)); }
    public static ModLoadingIssue warning(String key, Object... args) { return new ModLoadingIssue(Severity.WARNING, key, List.of(args)); }
    public ModLoadingIssue withAffectedPath(Path p) { return new ModLoadingIssue(severity, translationKey, translationArgs, cause, p, affectedModFile, affectedMod); }
    public ModLoadingIssue withAffectedModFile(IModFile f) { return new ModLoadingIssue(severity, translationKey, translationArgs, cause, affectedPath, f, affectedMod); }
    public ModLoadingIssue withAffectedMod(IModInfo m) { return new ModLoadingIssue(severity, translationKey, translationArgs, cause, affectedPath, affectedModFile, m); }
    public ModLoadingIssue withCause(Throwable t) { return new ModLoadingIssue(severity, translationKey, translationArgs, t, affectedPath, affectedModFile, affectedMod); }
    public ModLoadingIssue withSeverity(Severity s) { return new ModLoadingIssue(s, translationKey, translationArgs, cause, affectedPath, affectedModFile, affectedMod); }
    public enum Severity { WARNING, ERROR }
    @Override public String toString() { return severity + ": " + net.neoforged.fml.i18n.FMLTranslations.translateIssueEnglish(this); }
}
