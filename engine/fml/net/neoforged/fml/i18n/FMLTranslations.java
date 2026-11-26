package net.neoforged.fml.i18n;
import net.neoforged.fml.ModLoadingIssue;
import java.text.MessageFormat; import java.util.*; import java.util.function.Supplier;
public class FMLTranslations {
    private static final Map<String, String> EN = new HashMap<>(Map.of(
            "fml.modloadingissue.failedtoloadmod", "Mod {0} failed to load correctly",
            "fml.modloadingissue.errorduringevent", "Mod {0} hit an error during {1}",
            "fml.modloadingissue.technical_error", "{0}"));
    private static Map<String, String> current = EN;
    static void inject(Map<String, String> m) { Map<String, String> n = new HashMap<>(EN); n.putAll(m); current = n; }
    public static String getPattern(String key, Supplier<String> fallback) { return current.getOrDefault(key, fallback.get()); }
    public static String parseMessage(String key, Object... args) { return parseFormat(getPattern(key, () -> key), args); }
    public static String parseMessageWithFallback(String key, Supplier<String> fb, Object... args) { return parseFormat(getPattern(key, fb), args); }
    public static String parseEnglishMessage(String key, Object... args) { return parseFormat(EN.getOrDefault(key, key), args); }
    public static String parseFormat(String format, Object... args) {
        try { return new MessageFormat(format).format(args); } catch (IllegalArgumentException e) { return format + " " + Arrays.toString(args); }
    }
    private static String issue(ModLoadingIssue i, boolean english) {
        Object[] args = i.translationArgs().toArray();
        String s = english ? parseEnglishMessage(i.translationKey(), args) : parseMessage(i.translationKey(), args);
        if (i.affectedMod() != null && !s.contains(i.affectedMod().getModId())) s = i.affectedMod().getModId() + ": " + s;
        if (i.cause() != null) s += " (" + i.cause() + ")";
        return s;
    }
    public static String translateIssueEnglish(ModLoadingIssue i) { return issue(i, true); }
    public static String translateIssue(ModLoadingIssue i) { return issue(i, false); }
    public static String stripControlCodes(String s) { return s.replaceAll("\u00a7.", ""); }
}
