package dev.omniloader.loader.convert;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.util.Map;

/** Applies a version bridge (Minecraft class renames) to code that already uses Mojang's names. */
public final class BridgeRemapper extends Remapper {
    private final Map<String, String> renames;

    public BridgeRemapper(Map<String, String> renames) { this.renames = renames; }

    public byte[] remapClass(byte[] in) {
        ClassReader cr = new ClassReader(in);
        ClassWriter cw = new ClassWriter(0);
        cr.accept(new ClassRemapper(cw, this), 0);
        return cw.toByteArray();
    }

    @Override public String map(String internalName) { return renames.getOrDefault(internalName, internalName); }

    /** Names written as text (mixin targets, reflection). */
    @Override public Object mapValue(Object value) {
        if (value instanceof String s && s.contains("net") && s.length() < 500) {
            for (var e : renames.entrySet()) {
                if (s.contains(e.getKey())) s = s.replace(e.getKey(), e.getValue());
                String dk = e.getKey().replace('/', '.');
                if (s.contains(dk)) s = s.replace(dk, e.getValue().replace('/', '.'));
            }
            return s;
        }
        return super.mapValue(value);
    }
}
