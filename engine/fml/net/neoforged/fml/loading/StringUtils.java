package net.neoforged.fml.loading;
import java.util.*;
public class StringUtils {
    public static String toLowerCase(String s) { return s.toLowerCase(Locale.ROOT); }
    public static String toUpperCase(String s) { return s.toUpperCase(Locale.ROOT); }
    public static boolean endsWith(String s, String... ends) { for (String e : ends) if (s.endsWith(e)) return true; return false; }
    public static java.net.URL toURL(String s) { try { return new java.net.URL(s); } catch (Exception e) { return null; } }
    public static String parseStringFormat(String s, Map<String, String> m) { for (var e : m.entrySet()) s = s.replace("${" + e.getKey() + "}", e.getValue()); return s; }
    public static String binToHex(byte[] b) { StringBuilder sb = new StringBuilder(); for (byte x : b) sb.append(String.format("%02x", x)); return sb.toString(); }
}
