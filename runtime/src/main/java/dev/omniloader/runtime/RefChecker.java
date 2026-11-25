package dev.omniloader.runtime;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * For mods made for a different Minecraft version: checks that every piece of Minecraft the mod uses
 * still exists in this game, before the mod is started. Returns what's missing (empty = all good).
 *
 * Reads the game's class files directly and never loads them: loading classes this early would stop
 * other mods' mixins from applying to them.
 */
public final class RefChecker {
    private RefChecker() {}

    private record Info(String sup, String[] itfs, Set<String> fields, Set<String> methods) {}
    private static final Map<String, Info> CACHE = new HashMap<>();
    private static final Info MISSING = new Info(null, new String[0], Set.of(), Set.of());

    /** Members other Fabric mods' mixins (and Omniloader itself) add to Minecraft classes: "owner name(desc)". */
    public static final Set<String> ADDED = new HashSet<>();

    public static void addProvided(String lines) {
        if (lines == null) return;
        for (String l : lines.split("\n")) {
            String[] p = l.trim().split(" ");
            if (p.length == 3 && p[0].equals("m")) ADDED.add(p[1] + " " + p[2]);
        }
    }

    public static List<String> missing(String refs) {
        List<String> out = new ArrayList<>();
        for (String line : refs.split("\n")) {
            String[] p = line.trim().split(" ");
            if (p.length < 2) continue;
            String owner = p[1];
            // Client-only parts of the game don't exist on a dedicated server; that's normal.
            if (!Platform.isClient() && (owner.startsWith("net/minecraft/client/") || owner.startsWith("com/mojang/blaze3d/"))) continue;
            if (owner.startsWith("java/") || owner.startsWith("javax/")) {
                // Java's own library: safe to look at directly. Matters for mods built for a newer Java.
                if (!jdkHas(p)) out.add("Java " + Runtime.version().feature() + " lacks " + simple(owner) + (p.length > 2 ? "." + p[2] + (p[0].equals("m") ? "()" : "") : ""));
                continue;
            }
            if (info(owner) == MISSING) { out.add(simple(owner)); continue; }
            if (p[0].equals("f") && !has(owner, p[2] + " " + p[3], true, new HashSet<>())) out.add(simple(owner) + "." + p[2]);
            if (p[0].equals("m") && !p[2].equals("<clinit>") && !ADDED.contains(owner + " " + p[2] + p[3])
                    && !has(owner, p[2] + p[3], false, new HashSet<>())) out.add(simple(owner) + "." + p[2] + "()");
        }
        return out.stream().distinct().toList();
    }

    private static boolean jdkHas(String[] p) {
        try {
            Class<?> c = Class.forName(p[1].replace('/', '.'), false, ClassLoader.getPlatformClassLoader());
            if (p[0].equals("c")) return true;
            if (p[0].equals("f")) {
                for (Class<?> k = c; k != null; k = k.getSuperclass()) for (var f : k.getDeclaredFields()) if (f.getName().equals(p[2])) return true;
                for (var f : c.getFields()) if (f.getName().equals(p[2])) return true;
                return false;
            }
            String want = p[2] + p[3];
            if (p[2].equals("<init>")) {
                for (var k : c.getDeclaredConstructors()) if (("<init>" + org.objectweb.asm.Type.getConstructorDescriptor(k)).equals(want)) return true;
                return false;
            }
            java.util.ArrayDeque<Class<?>> todo = new java.util.ArrayDeque<>(List.of(c));
            Set<Class<?>> seen = new HashSet<>();
            while (!todo.isEmpty()) {
                Class<?> k = todo.poll();
                if (k == null || !seen.add(k)) continue;
                for (var m : k.getDeclaredMethods()) if ((m.getName() + org.objectweb.asm.Type.getMethodDescriptor(m)).equals(want)) return true;
                todo.add(k.getSuperclass());
                todo.addAll(List.of(k.getInterfaces()));
            }
            return c.isInterface() && p[2].matches("hashCode|equals|toString|getClass");
        } catch (Throwable t) {
            return false;
        }
    }

    private static String simple(String internal) { return internal.substring(internal.lastIndexOf('/') + 1); }

    private static boolean has(String owner, String key, boolean field, Set<String> seen) {
        if (owner == null || !seen.add(owner)) return false;
        Info i = info(owner);
        if (i == MISSING) return owner.startsWith("java/"); // JDK types: trust them
        if ((field ? i.fields : i.methods).contains(key)) return true;
        if (!field && key.startsWith("<init>")) return false; // constructors are not inherited
        if (has(i.sup, key, field, seen)) return true;
        for (String it : i.itfs) if (has(it, key, field, seen)) return true;
        return false;
    }

    private static synchronized Info info(String owner) {
        return CACHE.computeIfAbsent(owner, o -> {
            ClassLoader cl = RefChecker.class.getClassLoader();
            try (InputStream in = cl.getResourceAsStream(o + ".class")) {
                if (in == null) return MISSING;
                Set<String> f = new HashSet<>(), m = new HashSet<>();
                String[] sup = new String[1];
                String[][] itfs = new String[1][];
                new ClassReader(in.readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public void visit(int v, int a, String n, String s, String superName, String[] interfaces) { sup[0] = superName; itfs[0] = interfaces; }
                    @Override public FieldVisitor visitField(int a, String n, String d, String s, Object val) { f.add(n + " " + d); return null; }
                    @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) { m.add(n + d); return null; }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return new Info(sup[0], itfs[0] == null ? new String[0] : itfs[0], f, m);
            } catch (Exception e) {
                return MISSING;
            }
        });
    }
}
