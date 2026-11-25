package dev.omniloader.engine;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.Map;

/** Applies mods' access changes (Fabric access wideners, already converted to "public/protected[-f] ..." lines). */
public final class AccessRules {
    private record Rule(int vis, boolean definal) {} // vis: 1 protected, 2 public
    private final Map<String, Rule> classes = new HashMap<>(), members = new HashMap<>();

    public void add(String text) {
        for (String raw : text.split("\n")) {
            String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')).trim() : raw.trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+");
            if (p.length < 2) continue;
            boolean definal = p[0].endsWith("-f");
            int vis = p[0].startsWith("public") ? 2 : 1;
            String owner = p[1].replace('.', '/');
            Rule r = new Rule(vis, definal);
            if (p.length == 2) merge(classes, owner, r);
            else merge(members, owner + " " + p[2], r);   // "name" (field) or "name(desc)" (method)
        }
    }

    private static void merge(Map<String, Rule> map, String key, Rule r) {
        map.merge(key, r, (a, b) -> new Rule(Math.max(a.vis, b.vis), a.definal || b.definal));
    }

    public boolean isEmpty() { return classes.isEmpty() && members.isEmpty(); }

    private static int apply(int access, Rule r) {
        if (r == null) return access;
        if (r.vis == 2) access = access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
        else if ((access & Opcodes.ACC_PUBLIC) == 0) access = access & ~Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED;
        if (r.definal) access &= ~Opcodes.ACC_FINAL;
        return access;
    }

    public byte[] transform(String internalName, byte[] in) {
        Rule cr = classes.get(internalName);
        boolean any = cr != null || members.keySet().stream().anyMatch(k -> k.startsWith(internalName + " "));
        if (!any) return in;
        ClassReader reader = new ClassReader(in);
        ClassWriter w = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, w) {
            @Override public void visit(int v, int access, String name, String sig, String sup, String[] itf) { super.visit(v, apply(access, cr), name, sig, sup, itf); }
            @Override public FieldVisitor visitField(int access, String name, String desc, String sig, Object val) {
                return super.visitField(apply(access, members.get(internalName + " " + name)), name, desc, sig, val);
            }
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                Rule r = members.get(internalName + " " + name + desc);
                // a widened private method becomes callable from outside: it must also stop being "private" in calls (invokespecial -> invokevirtual is handled by the JVM for nestmates; good enough here)
                return super.visitMethod(apply(access, r), name, desc, sig, ex);
            }
            @Override public void visitInnerClass(String name, String outer, String inner, int access) { super.visitInnerClass(name, outer, inner, apply(access, classes.get(name))); }
        }, 0);
        return w.toByteArray();
    }
}
