package dev.omniloader.loader.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Points a newer mod's calls to things Minecraft added after this version at Omniloader's stand-ins
 * (backports.txt + dev.omniloader.runtime.backport.Backports).
 */
public final class Backporter {
    private static final String HELPER = "dev/omniloader/runtime/backport/Backports";

    private record MethodRule(String owner, String name, String desc, String helper, String helperDesc) {}
    private record FieldRule(String owner, String name, String desc) {}

    private static final List<MethodRule> METHODS = new ArrayList<>();
    private static final List<FieldRule> FIELDS = new ArrayList<>();
    private static final List<MethodRule> FORGE_METHODS = new ArrayList<>();
    private static final List<FieldRule> FORGE_FIELDS = new ArrayList<>();

    static {
        load("/backports.txt", METHODS, FIELDS);
        load("/backports-forge.txt", FORGE_METHODS, FORGE_FIELDS);
    }

    private static void load(String resource, List<MethodRule> methods, List<FieldRule> fields) {
        try (InputStream in = Backporter.class.getResourceAsStream(resource)) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] p = line.split("\\s+");
                    if (p[0].equals("m") && p.length == 7) methods.add(new MethodRule(p[1], p[2], p[3], p[5], p[6]));
                    if (p[0].equals("f") && p.length == 6) fields.add(new FieldRule(p[1], p[2], p[3]));
                }
            }
        } catch (Exception ignored) {}
    }

    public static boolean any() { return !METHODS.isEmpty() || !FIELDS.isEmpty(); }

    /** Newer-Minecraft calls -> stand-ins (backports.txt). */
    public static byte[] apply(byte[] in) { return apply(in, METHODS, FIELDS); }

    /** Forge-vs-NeoForge differences -> stand-ins (backports-forge.txt). */
    public static byte[] applyForge(byte[] in) { return apply(in, FORGE_METHODS, FORGE_FIELDS); }

    private static byte[] apply(byte[] in, List<MethodRule> METHODS, List<FieldRule> FIELDS) {
        if (METHODS.isEmpty() && FIELDS.isEmpty()) return in;
        ClassReader cr = new ClassReader(in);
        ClassWriter cw = new ClassWriter(0);
        boolean[] changed = {false};
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, sig, ex)) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String mname, String mdesc, boolean itf) {
                        if (owner.startsWith("net/minecraft/") || owner.startsWith("net/minecraftforge/") || owner.startsWith("net/neoforged/") || owner.startsWith("net/fabricmc/")) {
                            for (MethodRule r : METHODS) {
                                // "*" rules are for calls on objects only; static calls need the exact owner
                                if (r.name.equals(mname) && r.desc.equals(mdesc) && (r.owner.equals(owner) || r.owner.equals("*") && op != Opcodes.INVOKESTATIC)) {
                                    changed[0] = true;
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, r.helper, r.helperDesc, false);
                                    return;
                                }
                            }
                        }
                        super.visitMethodInsn(op, owner, mname, mdesc, itf);
                    }

                    @Override
                    public void visitFieldInsn(int op, String owner, String fname, String fdesc) {
                        for (FieldRule r : FIELDS) {
                            if (r.owner.equals(owner) && r.name.equals(fname) && r.desc.equals(fdesc)) {
                                changed[0] = true;
                                Type t = Type.getType(fdesc);
                                boolean wide = t.getSize() == 2;
                                switch (op) {
                                    case Opcodes.GETFIELD -> { super.visitInsn(Opcodes.POP); zero(t); }
                                    case Opcodes.GETSTATIC -> zero(t);
                                    case Opcodes.PUTFIELD -> { super.visitInsn(wide ? Opcodes.POP2 : Opcodes.POP); super.visitInsn(Opcodes.POP); }
                                    case Opcodes.PUTSTATIC -> super.visitInsn(wide ? Opcodes.POP2 : Opcodes.POP);
                                    default -> super.visitFieldInsn(op, owner, fname, fdesc);
                                }
                                return;
                            }
                        }
                        super.visitFieldInsn(op, owner, fname, fdesc);
                    }

                    private void zero(Type t) {
                        switch (t.getSort()) {
                            case Type.LONG -> super.visitInsn(Opcodes.LCONST_0);
                            case Type.FLOAT -> super.visitInsn(Opcodes.FCONST_0);
                            case Type.DOUBLE -> super.visitInsn(Opcodes.DCONST_0);
                            case Type.OBJECT, Type.ARRAY -> super.visitInsn(Opcodes.ACONST_NULL);
                            default -> super.visitInsn(Opcodes.ICONST_0);
                        }
                    }
                };
            }
        }, 0);
        return changed[0] ? cw.toByteArray() : in;
    }
}
