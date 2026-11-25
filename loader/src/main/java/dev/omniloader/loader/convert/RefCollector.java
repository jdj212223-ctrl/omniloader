package dev.omniloader.loader.convert;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lists every piece of Minecraft a mod's code touches (classes, fields, methods), including the members
 * its mixins "@Shadow". Used for mods made for a different Minecraft version, to check before starting them
 * that everything they need still exists.
 * Lines: "c owner" | "f owner name desc" | "m owner name desc"
 */
public final class RefCollector {
    private RefCollector() {}

    static boolean game(String owner) {
        return owner != null && (owner.startsWith("net/minecraft/") || owner.startsWith("com/mojang/blaze3d/") || owner.startsWith("net/neoforged/neoforge/")
                || owner.startsWith("net/fabricmc/fabric/api/") || owner.startsWith("net/fabricmc/loader/api/") || owner.startsWith("org/quiltmc/"));
    }

    static boolean jdk(String owner) {
        return owner != null && (owner.startsWith("java/") || owner.startsWith("javax/"));
    }

    /** @param adds receives members this class (if it's a mixin) adds to Minecraft classes. */
    public static void collect(byte[] cls, Set<String> out, Set<String> adds) { collect(cls, out, adds, false); }

    /** @param withJdk also list Java library calls (for code built for a newer Java than the game runs on) */
    public static void collect(byte[] cls, Set<String> out, Set<String> adds, boolean withJdk) {
        ClassReader cr = new ClassReader(cls);
        List<String> mixinTargets = new ArrayList<>();
        List<String> ownInterfaces = new ArrayList<>();
        String[] self = new String[1];
        boolean[] isInterface = new boolean[1];
        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int v, int a, String name, String sig, String sup, String[] itfs) {
                self[0] = name;
                isInterface[0] = (a & Opcodes.ACC_INTERFACE) != 0;
                if (game(sup)) out.add("c " + sup);
                if (itfs != null) for (String i : itfs) { ownInterfaces.add(i); if (game(i)) out.add("c " + i); }
            }

            @Override
            public void visitEnd() {
                // A mixin's interfaces end up on its target class (interface injection), and so does a mixin interface itself.
                for (String t : mixinTargets) {
                    for (String i : ownInterfaces) adds.add("i " + t + " " + i);
                    if (isInterface[0]) adds.add("i " + t + " " + self[0]);
                }
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (!desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return null;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitArray(String n) {
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override public void visit(String x, Object val) {
                                String t = val instanceof Type ty ? ty.getInternalName() : String.valueOf(val).replace('.', '/');
                                if (game(t)) { mixinTargets.add(t); out.add("c " + t); }
                            }
                        };
                    }
                };
            }

            private boolean shadow(String d) { return d.equals("Lorg/spongepowered/asm/mixin/Shadow;"); }

            @Override
            public FieldVisitor visitField(int access, String fname, String fdesc, String sig, Object value) {
                return new FieldVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String d, boolean v) {
                        if (shadow(d)) for (String t : mixinTargets) out.add("f " + t + " " + fname + " " + fdesc);
                        return null;
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(int access, String mname, String mdesc, String sig, String[] ex) {
                boolean candidate = !mixinTargets.isEmpty() && (access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC)) == 0
                        && !mname.startsWith("<");
                boolean[] annotated = {false};
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitEnd() {
                        // A plain public method in a mixin is copied into the target class.
                        if (candidate && !annotated[0]) for (String t : mixinTargets) adds.add("m " + t + " " + mname + mdesc);
                    }
                    @Override public AnnotationVisitor visitAnnotation(String d, boolean v) {
                        if (!d.equals("Lorg/spongepowered/asm/mixin/Unique;")) annotated[0] = true;
                        if (shadow(d)) {
                            String n = mname.startsWith("shadow$") ? mname.substring(7) : mname;
                            for (String t : mixinTargets) out.add("m " + t + " " + n + " " + mdesc);
                        }
                        return null;
                    }
                    @Override public void visitFieldInsn(int op, String owner, String name, String desc) {
                        if (game(owner) || withJdk && jdk(owner)) out.add("f " + owner + " " + name + " " + desc);
                    }
                    @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        if ((game(owner) || withJdk && jdk(owner)) && !owner.startsWith("[")) out.add("m " + owner + " " + name + " " + desc);
                    }
                    @Override public void visitTypeInsn(int op, String type) {
                        if (game(type) || withJdk && jdk(type)) out.add("c " + type);
                    }
                    @Override public void visitInvokeDynamicInsn(String n, String d, Handle bsm, Object... args) {
                        for (Object a : args) if (a instanceof Handle h && game(h.getOwner()))
                            out.add((h.getTag() <= Opcodes.H_PUTSTATIC ? "f " : "m ") + h.getOwner() + " " + h.getName() + " " + h.getDesc());
                    }
                };
            }
        }, ClassReader.SKIP_FRAMES);
    }
}
