package net.neoforged.fml.i18n;
import java.util.*;
public class I18nManager {
    public static void injectTranslations(Map<String, String> m) { FMLTranslations.inject(m); }
    public static Map<String, String> loadTranslations(String lang) { return Map.of(); }
}
