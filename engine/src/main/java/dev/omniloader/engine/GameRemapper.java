package dev.omniloader.engine;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Turns Mojang's scrambled game jar into one with Mojang's real names, using Mojang's own name list.
 * Done once per game jar; the result is cached. This is Omniloader's equivalent of what NeoForge's installer
 * and Fabric's loader do before the game can be modded.
 */
public final class GameRemapper extends Remapper {
    private final Map<String, String> classes = new HashMap<>();              // obf -> named
    private final Map<String, Map<String, String>> fields = new HashMap<>();  // obf owner -> obf name -> named
    private final Map<String, Map<String, String>> methods = new HashMap<>(); // obf owner -> obf name+obf desc -> named
    private final Map<String, String[]> hierarchy = new HashMap<>();          // obf class -> [super, interfaces...]

    public static Path ensure(Path gameJar, Path mojangMappings, Path cacheDir) throws IOException {
        Files.createDirectories(cacheDir);
        String key = Long.toHexString(Files.size(gameJar)) + "-" + Long.toHexString(Files.getLastModifiedTime(gameJar).toMillis());
        Path out = cacheDir.resolve("minecraft-mojang-" + key + ".jar");
        if (Files.isRegularFile(out)) return out;
        long t0 = System.currentTimeMillis();
        Engine.log("Translating Minecraft to Mojang's names (first start only)...");
        GameRemapper r = new GameRemapper();
        r.readMappings(mojangMappings);
        Path tmp = Files.createTempFile(cacheDir, "mc", ".tmp");
        try (ZipFile in = new ZipFile(gameJar.toFile())) {
            for (var e : java.util.Collections.list(in.entries())) {
                if (!e.getName().endsWith(".class")) continue;
                ClassReader cr = new ClassReader(in.getInputStream(e).readAllBytes());
                String[] h = new String[cr.getInterfaces().length + 1];
                h[0] = cr.getSuperName();
                System.arraycopy(cr.getInterfaces(), 0, h, 1, cr.getInterfaces().length);
                r.hierarchy.put(cr.getClassName(), h);
            }
            try (OutputStream fo = Files.newOutputStream(tmp); JarOutputStream jo = new JarOutputStream(fo)) {
                Set<String> written = new HashSet<>();
                for (var e : java.util.Collections.list(in.entries())) {
                    String n = e.getName();
                    if (e.isDirectory() || n.startsWith("META-INF/") && (n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".MF"))) continue;
                    byte[] b = in.getInputStream(e).readAllBytes();
                    if (n.endsWith(".class")) {
                        ClassReader cr = new ClassReader(b);
                        ClassWriter cw = new ClassWriter(0);
                        cr.accept(new ClassRemapper(cw, r), 0);
                        b = cw.toByteArray();
                        n = r.map(cr.getClassName()) + ".class";
                    }
                    if (!written.add(n)) continue;
                    jo.putNextEntry(new ZipEntry(n));
                    jo.write(b);
                    jo.closeEntry();
                }
            }
        }
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
        Engine.log("Minecraft translated in " + (System.currentTimeMillis() - t0) / 1000 + " s");
        return out;
    }

    /** Mojang's ProGuard-format list: "named -> obf:" for classes, "    type name(args) -> obf" for members. */
    private void readMappings(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        Map<String, String> namedToObf = new HashMap<>();
        for (String l : lines) {
            if (l.startsWith("#") || l.startsWith(" ") || l.isBlank()) continue;
            int a = l.indexOf(" -> ");
            String named = l.substring(0, a).replace('.', '/'), obf = l.substring(a + 4, l.length() - 1).replace('.', '/');
            classes.put(obf, named);
            namedToObf.put(named, obf);
        }
        String owner = null;
        for (String l : lines) {
            if (l.startsWith("#") || l.isBlank()) continue;
            if (!l.startsWith(" ")) { owner = l.substring(l.indexOf(" -> ") + 4, l.length() - 1).replace('.', '/'); continue; }
            String s = l.trim();
            if (s.startsWith("#")) continue;
            int arrow = s.indexOf(" -> ");
            String obfName = s.substring(arrow + 4), left = s.substring(0, arrow);
            while (!left.isEmpty() && Character.isDigit(left.charAt(0))) left = left.substring(left.indexOf(':') + 1);
            int sp = left.indexOf(' ');
            String type = left.substring(0, sp), rest = left.substring(sp + 1);
            int paren = rest.indexOf('(');
            if (paren >= 0) {
                String name = rest.substring(0, paren), args = rest.substring(paren + 1, rest.indexOf(')'));
                StringBuilder d = new StringBuilder("(");
                if (!args.isEmpty()) for (String x : args.split(",")) d.append(desc(x, namedToObf));
                d.append(')').append(desc(type, namedToObf));
                methods.computeIfAbsent(owner, k -> new HashMap<>()).put(obfName + d, name);
            } else {
                fields.computeIfAbsent(owner, k -> new HashMap<>()).put(obfName, rest);
            }
        }
    }

    private static String desc(String t, Map<String, String> namedToObf) {
        int dims = 0;
        while (t.endsWith("[]")) { dims++; t = t.substring(0, t.length() - 2); }
        String base = switch (t) {
            case "void" -> "V"; case "boolean" -> "Z"; case "byte" -> "B"; case "char" -> "C";
            case "short" -> "S"; case "int" -> "I"; case "long" -> "J"; case "float" -> "F"; case "double" -> "D";
            default -> { String n = t.replace('.', '/'); yield "L" + namedToObf.getOrDefault(n, n) + ";"; }
        };
        return "[".repeat(dims) + base;
    }

    @Override public String map(String internalName) { return classes.getOrDefault(internalName, internalName); }

    /** Finds a member's real name in the class that declares it (walking up through parent classes and interfaces). */
    private String resolve(String owner, String key, boolean field) {
        ArrayDeque<String> todo = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        todo.add(owner);
        while (!todo.isEmpty()) {
            String c = todo.poll();
            if (c == null || !seen.add(c)) continue;
            Map<String, String> m = (field ? fields : methods).get(c);
            if (m != null) { String r = m.get(key); if (r != null) return r; }
            String[] h = hierarchy.get(c);
            if (h != null) for (String p : h) if (p != null) todo.add(p);
        }
        return null;
    }

    @Override public String mapMethodName(String owner, String name, String descriptor) {
        if (name.startsWith("<")) return name;
        String r = resolve(owner, name + descriptor, false);
        return r != null ? r : name;
    }

    @Override public String mapFieldName(String owner, String name, String descriptor) {
        String r = resolve(owner, name, true);
        return r != null ? r : name;
    }

    @Override public String mapRecordComponentName(String owner, String name, String descriptor) { return mapFieldName(owner, name, descriptor); }

    /** Lambdas: the method name belongs to the functional interface the lambda turns into. */
    @Override public String mapInvokeDynamicMethodName(String name, String descriptor) {
        Type ret = Type.getReturnType(descriptor);
        if (ret.getSort() != Type.OBJECT) return name;
        String itf = ret.getInternalName();
        Map<String, String> m = methods.get(itf);
        if (m != null) for (var e : m.entrySet()) if (e.getKey().startsWith(name + "(")) return e.getValue();
        return name;
    }
}
