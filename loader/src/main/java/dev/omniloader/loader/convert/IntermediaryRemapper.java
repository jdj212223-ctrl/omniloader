package dev.omniloader.loader.convert;

import dev.omniloader.loader.mapping.Mappings;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.util.regex.Pattern;

/** Renames every Fabric-style name inside a compiled class to Mojang's name. */
public final class IntermediaryRemapper extends Remapper {
    private static final Pattern CLASS_STRING = Pattern.compile("net[./]minecraft[./]class_\\d+(?:\\$class_\\d+)*");
    private static final Pattern MEMBER_STRING = Pattern.compile("(?:method|field|comp)_\\d+");
    private final Mappings m;

    public IntermediaryRemapper(Mappings m) { this.m = m; }

    public byte[] remapClass(byte[] in) {
        ClassReader cr = new ClassReader(in);
        ClassWriter cw = new ClassWriter(0);
        cr.accept(new ClassRemapper(cw, this), 0);
        return cw.toByteArray();
    }

    @Override public String map(String internalName) {
        if (internalName.startsWith("net/minecraft/class_") || internalName.contains("$class_")) return m.mapClass(internalName);
        return m.bridge.isEmpty() ? internalName : m.bridge.getOrDefault(internalName, internalName);
    }
    @Override public String mapMethodName(String owner, String name, String descriptor) { return m.mapMethod(name); }
    @Override public String mapInvokeDynamicMethodName(String name, String descriptor) { return m.mapMethod(name); }
    @Override public String mapFieldName(String owner, String name, String descriptor) { return m.mapField(name); }
    @Override public String mapRecordComponentName(String owner, String name, String descriptor) { return m.mapMethod(name); }

    /** Also fix plain strings that are exactly an intermediary name (used with reflection / Class.forName). */
    @Override public Object mapValue(Object value) {
        if (value instanceof String s && s.length() < 200) {
            if (CLASS_STRING.matcher(s).matches()) {
                boolean dots = s.indexOf('.') >= 0;
                String r = m.mapClass(s.replace('.', '/'));
                return dots ? r.replace('/', '.') : r;
            }
            if (MEMBER_STRING.matcher(s).matches()) {
                return s.startsWith("field_") ? m.mapField(s) : m.mapMethod(s);
            }
            // Mixin targets written straight into annotations (newer Fabric toolchains, no refmap),
            // e.g. "<init>(Lnet/minecraft/class_542;)V" or "Lnet/minecraft/class_310;method_1234()V".
            if (s.contains("class_") || s.contains("method_") || s.contains("field_")) return m.mapText(s);
        }
        return super.mapValue(value);
    }
}
