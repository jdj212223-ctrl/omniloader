package cpw.mods.modlauncher.api;
import org.objectweb.asm.tree.*;
public final class TargetType<T> {
    public static final TargetType<ClassNode> PRE_CLASS = new TargetType<>("PRE_CLASS", ClassNode.class);
    public static final TargetType<ClassNode> CLASS = new TargetType<>("CLASS", ClassNode.class);
    public static final TargetType<MethodNode> METHOD = new TargetType<>("METHOD", MethodNode.class);
    public static final TargetType<FieldNode> FIELD = new TargetType<>("FIELD", FieldNode.class);
    public static final TargetType<?>[] VALUES = {PRE_CLASS, CLASS, METHOD, FIELD};
    private final String name; private final Class<T> type;
    private TargetType(String name, Class<T> type) { this.name = name; this.type = type; }
    public Class<T> getNodeType() { return type; }
    public String name() { return name; }
    public static TargetType<?> byName(String n) { for (TargetType<?> t : VALUES) if (t.name.equals(n)) return t; throw new IllegalArgumentException(n); }
    @Override public String toString() { return name; }
}
