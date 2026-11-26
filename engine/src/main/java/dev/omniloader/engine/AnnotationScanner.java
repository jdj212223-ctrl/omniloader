package dev.omniloader.engine;

import net.neoforged.fml.loading.moddiscovery.ModAnnotation;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.annotation.ElementType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Reads every class in a mod (without loading it) and notes its annotations, like @Mod and @EventBusSubscriber. */
final class AnnotationScanner {
    private AnnotationScanner() {}

    static ModFileScanData scan(Path root) {
        ModFileScanData data = new ModFileScanData();
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".class") && !x.toString().startsWith("/META-INF/")).toList()) {
                try { new ClassReader(Files.readAllBytes(p)).accept(new Scanner(data), ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES); }
                catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Engine.log("Could not scan " + root + ": " + e);
        }
        return data;
    }

    private static final class Scanner extends ClassVisitor {
        private final ModFileScanData data;
        private Type clazz;
        Scanner(ModFileScanData data) { super(Opcodes.ASM9); this.data = data; }

        @Override public void visit(int v, int access, String name, String sig, String sup, String[] itf) {
            clazz = Type.getObjectType(name);
            Set<Type> is = new LinkedHashSet<>();
            if (itf != null) for (String i : itf) is.add(Type.getObjectType(i));
            data.getClasses().add(new ModFileScanData.ClassData(clazz, sup == null ? null : Type.getObjectType(sup), is));
        }

        private AnnotationVisitor record(String desc, ElementType kind, String member) {
            Map<String, Object> values = new HashMap<>();
            data.getAnnotations().add(new ModFileScanData.AnnotationData(Type.getType(desc), kind, clazz, member, values));
            return new Values(values::put);
        }

        @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) { return record(desc, ElementType.TYPE, clazz.getClassName()); }
        @Override public FieldVisitor visitField(int access, String name, String desc, String sig, Object value) {
            return new FieldVisitor(Opcodes.ASM9) { @Override public AnnotationVisitor visitAnnotation(String d, boolean v) { return record(d, ElementType.FIELD, name); } };
        }
        @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
            return new MethodVisitor(Opcodes.ASM9) { @Override public AnnotationVisitor visitAnnotation(String d, boolean v) { return record(d, ElementType.METHOD, name + desc); } };
        }
    }

    /** Collects annotation values the way NeoForge's tools expect them (enums as EnumHolder, arrays as lists). */
    private static final class Values extends AnnotationVisitor {
        private final java.util.function.BiConsumer<String, Object> out;
        Values(java.util.function.BiConsumer<String, Object> out) { super(Opcodes.ASM9); this.out = out; }
        @Override public void visit(String name, Object value) {
            if (value != null && value.getClass().isArray() && value.getClass().getComponentType().isPrimitive()) {
                List<Object> l = new ArrayList<>();
                for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++) l.add(java.lang.reflect.Array.get(value, i));
                out.accept(name, l);
            } else out.accept(name, value);
        }
        @Override public void visitEnum(String name, String desc, String value) { out.accept(name, new ModAnnotation.EnumHolder(desc, value)); }
        @Override public AnnotationVisitor visitAnnotation(String name, String desc) {
            Map<String, Object> m = new HashMap<>();
            out.accept(name, m);
            return new Values(m::put);
        }
        @Override public AnnotationVisitor visitArray(String name) {
            List<Object> l = new ArrayList<>();
            out.accept(name, l);
            Consumer<Object> add = l::add;
            return new Values((k, v) -> add.accept(v));
        }
    }
}
