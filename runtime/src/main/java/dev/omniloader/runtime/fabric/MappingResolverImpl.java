package dev.omniloader.runtime.fabric;

import net.fabricmc.loader.api.MappingResolver;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets Fabric mods translate names at runtime. The game runs on Mojang's names here,
 * so "intermediary" names (what Fabric mods are built against) are translated to those.
 */
public final class MappingResolverImpl implements MappingResolver {
    private final Map<String, String> intToNamed = new HashMap<>(), namedToInt = new HashMap<>();
    private final Map<String, String> obfToInt = new HashMap<>(), intToObf = new HashMap<>();
    private final Map<String, String> methods = new HashMap<>(), fields = new HashMap<>();
    private boolean loaded;

    private synchronized void load() {
        if (loaded) return;
        loaded = true;
        String p = System.getProperty("omniloader.mappings");
        if (p == null || !Files.isRegularFile(Path.of(p))) return;
        try (BufferedReader r = Files.newBufferedReader(Path.of(p))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] s = line.split("\t");
                switch (s[0]) {
                    case "c" -> {
                        String obf = s[1].replace('/', '.'), in = s[2].replace('/', '.'), named = s[3].replace('/', '.');
                        intToNamed.put(in, named); namedToInt.put(named, in); obfToInt.put(obf, in); intToObf.put(in, obf);
                    }
                    case "m" -> methods.put(s[1], s[2]);
                    case "f" -> fields.put(s[1], s[2]);
                    default -> {}
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Omniloader could not read its name table", e);
        }
    }

    public Collection<String> getNamespaces() { return List.of("intermediary", "named", "official"); }
    public String getCurrentRuntimeNamespace() { return "named"; }

    public String mapClassName(String ns, String className) {
        load();
        return switch (ns) {
            case "intermediary" -> intToNamed.getOrDefault(className, className);
            case "official" -> { String i = obfToInt.get(className); yield i == null ? className : intToNamed.getOrDefault(i, i); }
            default -> className;
        };
    }

    public String unmapClassName(String target, String className) {
        load();
        return switch (target) {
            case "intermediary" -> namedToInt.getOrDefault(className, className);
            case "official" -> { String i = namedToInt.get(className); yield i == null ? className : intToObf.getOrDefault(i, className); }
            default -> className;
        };
    }

    public String mapFieldName(String ns, String owner, String name, String desc) {
        load();
        return ns.equals("intermediary") ? fields.getOrDefault(name, name) : name;
    }

    public String mapMethodName(String ns, String owner, String name, String desc) {
        load();
        return ns.equals("intermediary") ? methods.getOrDefault(name, name) : name;
    }
}
