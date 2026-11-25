package dev.omniloader.loader.mapping;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translation table from Fabric's "intermediary" names (net/minecraft/class_310, method_1234, field_5678)
 * to Mojang's official names, which is what NeoForge uses at runtime.
 *
 * Intermediary member names are unique across the whole game (one name per method "family"),
 * so members can be looked up by name alone, without knowing the owning class.
 */
public final class Mappings {
    public final Map<String, String> classes = new HashMap<>();   // intermediary internal name -> mojang internal name
    public final Map<String, String> classesObf = new HashMap<>(); // intermediary internal name -> obfuscated name
    public final Map<String, String> methods = new HashMap<>();   // method_123 / comp_1 -> name
    public final Map<String, String> fields = new HashMap<>();    // field_123 / comp_1 -> name

    private static final Pattern CLASS = Pattern.compile("net/minecraft/class_\\d+(?:\\$class_\\d+)*");
    private static final Pattern CLASS_DOT = Pattern.compile("net\\.minecraft\\.class_\\d+(?:\\$class_\\d+)*");
    private static final Pattern MEMBER = Pattern.compile("\\b(method_\\d+|field_\\d+|comp_\\d+)\\b");

    /** Extra renames applied after translation (a "version bridge" to a newer Minecraft). */
    public final Map<String, String> bridge = new HashMap<>();

    public Mappings withBridge(Map<String, String> renames) {
        Mappings m = new Mappings();
        m.classes.putAll(classes); m.classesObf.putAll(classesObf); m.methods.putAll(methods); m.fields.putAll(fields);
        m.bridge.putAll(renames);
        return m;
    }

    public String mapClass(String internal) {
        String m = classes.get(internal);
        if (m != null) return bridge.getOrDefault(m, m);
        // Inner class we don't know by full name: map the outer part.
        int i = internal.lastIndexOf('$');
        if (i > 0) {
            String outer = mapClass(internal.substring(0, i));
            if (!outer.equals(internal.substring(0, i))) return outer + internal.substring(i);
        }
        return internal;
    }

    public String mapMethod(String name) {
        if (name.startsWith("method_") || name.startsWith("comp_")) return methods.getOrDefault(name, name);
        return name;
    }

    public String mapField(String name) {
        if (name.startsWith("field_") || name.startsWith("comp_")) return fields.getOrDefault(name, name);
        return name;
    }

    /** Rewrites every intermediary name found inside free text (refmaps, descriptors, access wideners). */
    public String mapText(String s) {
        if (s == null || !(s.contains("class_") || s.contains("method_") || s.contains("field_") || s.contains("comp_"))) return s;
        Matcher m = CLASS.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(mapClass(m.group())));
        m.appendTail(sb);
        s = sb.toString();

        m = CLASS_DOT.matcher(s);
        sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(mapClass(m.group().replace('.', '/')).replace('/', '.')));
        m.appendTail(sb);
        s = sb.toString();

        m = MEMBER.matcher(s);
        sb = new StringBuilder();
        while (m.find()) {
            String n = m.group(1);
            String r = n.startsWith("field_") ? mapField(n) : n.startsWith("method_") ? mapMethod(n) : methods.getOrDefault(n, fields.getOrDefault(n, n));
            m.appendReplacement(sb, Matcher.quoteReplacement(r));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
