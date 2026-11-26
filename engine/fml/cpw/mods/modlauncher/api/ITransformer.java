package cpw.mods.modlauncher.api;
import java.util.Set;
/** Omniloader's own copy of this interface (NeoForge's coremods implement it). */
public interface ITransformer<T> {
    String[] DEFAULT_LABEL = {"default"};
    T transform(T input, ITransformerVotingContext context);
    TransformerVoteResult castVote(ITransformerVotingContext context);
    Set<Target<T>> targets();
    TargetType<T> getTargetType();
    default String[] labels() { return DEFAULT_LABEL; }
    record Target<T>(String className, String elementName, String elementDescriptor, TargetType<T> targetType) {
        public static Target<org.objectweb.asm.tree.ClassNode> targetClass(String className) { return new Target<>(className, null, null, TargetType.CLASS); }
        public static Target<org.objectweb.asm.tree.ClassNode> targetPreClass(String className) { return new Target<>(className, null, null, TargetType.PRE_CLASS); }
        public static Target<org.objectweb.asm.tree.MethodNode> targetMethod(String className, String methodName, String desc) { return new Target<>(className, methodName, desc, TargetType.METHOD); }
        public static Target<org.objectweb.asm.tree.FieldNode> targetField(String className, String fieldName) { return new Target<>(className, fieldName, null, TargetType.FIELD); }
        public String getClassName() { return className; }
        public String getElementName() { return elementName; }
        public String getElementDescriptor() { return elementDescriptor; }
        public TargetType<T> getTargetType() { return targetType; }
    }
}
