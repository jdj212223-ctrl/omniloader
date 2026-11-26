package net.neoforged.neoforgespi.language;
import org.objectweb.asm.Type;
import java.lang.annotation.ElementType; import java.util.*; import java.util.stream.Stream;
public class ModFileScanData {
    private final Set<AnnotationData> annotations = new LinkedHashSet<>();
    private final Set<ClassData> classes = new LinkedHashSet<>();
    private final List<IModFileInfo> modFiles = new ArrayList<>();
    public Set<ClassData> getClasses() { return classes; }
    public Set<AnnotationData> getAnnotations() { return annotations; }
    public Stream<AnnotationData> getAnnotatedBy(Class<? extends java.lang.annotation.Annotation> type, ElementType kind) {
        Type t = Type.getType(type);
        return annotations.stream().filter(a -> a.targetType() == kind && a.annotationType().equals(t));
    }
    public void addModFileInfo(IModFileInfo info) { modFiles.add(info); }
    public List<IModFileInfo> getIModInfoData() { return modFiles; }
    public record AnnotationData(Type annotationType, ElementType targetType, Type clazz, String memberName, Map<String, Object> annotationData) {}
    public record ClassData(Type clazz, Type parent, Set<Type> interfaces) {}
}
