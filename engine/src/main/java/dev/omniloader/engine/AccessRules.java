package dev.omniloader.engine;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.Map;

/**
 * Applies access changes ("access transformers") from NeoForge, NeoForge/Forge mods and converted Fabric access wideners.
 * Line format: {@code <public|protected|default|private>[-f|+f] <class> [field | method(desc) | * | *()]}.
 * Only ever opens things up (never makes them more private).
 */
public final class AccessRules {
    /** vis: 0 private, 1 default, 2 protected, 3 public, -1 unchanged. fin: -1 remove final, 1 add final, 0 unchanged. */
    private record Rule(int vis, int fin) {
        Rule merge(Rule o) { return new Rule(Math.max(vis, o.vis), fin == -1 || o.fin == -1 ? -1 : Math.max(fin, o.fin)); }
    }

    private static final class Owner {
        Rule self, allFields, allMethods;
        final Map<String, Rule> members = new HashMap<>();
    }

    private final Map<String, Owner> owners = new HashMap<>();

    public synchronized void add(String text) {
        for (String raw : text.split("\n")) {
            String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')).trim() : raw.trim();
            if (line.isEmpty()) continue;
            String[] p = line.split("\\s+");
            if (p.length < 2) continue;
            String mod = p[0];
            int fin = mod.endsWith("-f") ? -1 : mod.endsWith("+f") ? 1 : 0;
            if (fin != 0) mod = mod.substring(0, mod.length() - 2);
            int vis = switch (mod) { case "public" -> 3; case "protected" -> 2; case "default" -> 1; case "private" -> 0; default -> -2; };
            if (vis == -2) continue;
            Rule r = new Rule(vis, fin);
            Owner o = owners.computeIfAbsent(p[1].replace('.', '/'), k -> new Owner());
            if (p.length == 2) o.self = o.self == null ? r : o.self.merge(r);
            else if (p[2].equals("*")) o.allFields = o.allFields == null ? r : o.allFields.merge(r);
            else if (p[2].equals("*()")) o.allMethods = o.allMethods == null ? r : o.allMethods.merge(r);
            else o.members.merge(p[2], r, Rule::merge);   // "name" (field) or "name(desc)ret" (method)
        }
    }

    public boolean isEmpty() { return owners.isEmpty(); }

    public boolean touches(String internalName) { return owners.containsKey(internalName); }

    private static int level(int access) {
        if ((access & Opcodes.ACC_PUBLIC) != 0) return 3;
        if ((access & Opcodes.ACC_PROTECTED) != 0) return 2;
        if ((access & Opcodes.ACC_PRIVATE) != 0) return 0;
        return 1;
    }

    private static int apply(int access, Rule r) {
        if (r == null) return access;
        if (r.vis > level(access)) {
            access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC);
            access |= switch (r.vis) { case 3 -> Opcodes.ACC_PUBLIC; case 2 -> Opcodes.ACC_PROTECTED; default -> 0; };
        }
        if (r.fin == -1) access &= ~Opcodes.ACC_FINAL;
        else if (r.fin == 1) access |= Opcodes.ACC_FINAL;
        return access;
    }

    private static Rule pick(Rule specific, Rule wildcard) {
        if (specific == null) return wildcard;
        return wildcard == null ? specific : specific.merge(wildcard);
    }

    public byte[] transform(String internalName, byte[] in) {
        Owner o = owners.get(internalName);
        boolean innerTouched = false;
        if (o == null) {
            // inner-class attributes of this class may still need changing (an inner class was opened up)
            return in;
        }
        ClassReader reader = new ClassReader(in);
        ClassWriter w = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, w) {
            @Override public void visit(int v, int access, String name, String sig, String sup, String[] itf) { super.visit(v, apply(access, o.self), name, sig, sup, itf); }
            @Override public FieldVisitor visitField(int access, String name, String desc, String sig, Object val) {
                return super.visitField(apply(access, pick(o.members.get(name), o.allFields)), name, desc, sig, val);
            }
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                Rule r = pick(o.members.get(name + desc), name.equals("<init>") || name.equals("<clinit>") ? null : o.allMethods);
                return super.visitMethod(apply(access, r), name, desc, sig, ex);
            }
            @Override public void visitInnerClass(String name, String outer, String inner, int access) {
                Owner io = owners.get(name);
                super.visitInnerClass(name, outer, inner, io == null ? access : apply(access, io.self));
            }
        }, 0);
        return w.toByteArray();
    }
}
