package dev.omniloader.loader.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Map;

/**
 * NeoForge replaces a few Minecraft fields with getter methods (so mods can modify biomes and structures).
 * Code that reads those fields directly would break, so we point it at the getters, just like NeoForge does.
 */
public final class NeoFieldGetters {
    private NeoFieldGetters() {}

    /** "owner.field" -> getter name. From NeoForge 21.1's NeoForgeCoreMod. */
    static final Map<String, String> GETTERS = Map.of(
            "net/minecraft/world/level/biome/Biome.climateSettings", "getModifiedClimateSettings",
            "net/minecraft/world/level/biome/Biome.specialEffects", "getModifiedSpecialEffects",
            "net/minecraft/world/level/levelgen/structure/Structure.settings", "getModifiedStructureSettings",
            "net/minecraft/world/level/block/FlowerPotBlock.potted", "getPotted");

    public static byte[] apply(byte[] in) {
        String text = new String(in, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!(text.contains("climateSettings") || text.contains("specialEffects") || text.contains("potted") || text.contains("settings"))) return in;
        ClassReader cr = new ClassReader(in);
        ClassWriter cw = new ClassWriter(0);
        boolean[] changed = {false};
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, sig, ex)) {
                    @Override
                    public void visitFieldInsn(int op, String owner, String fname, String fdesc) {
                        String getter = op == Opcodes.GETFIELD ? GETTERS.get(owner + "." + fname) : null;
                        if (getter != null) {
                            changed[0] = true;
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, getter, "()" + fdesc, false);
                        } else super.visitFieldInsn(op, owner, fname, fdesc);
                    }
                };
            }
        }, 0);
        return changed[0] ? cw.toByteArray() : in;
    }
}
