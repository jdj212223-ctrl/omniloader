package net.neoforged.fml.common.asm.enumextension;
import java.util.List;
public final class EnumProxy<T extends Enum<T> & IExtensibleEnum> {
    private final Class<T> enumClass; private final List<Object> parameters; private T value;
    public EnumProxy(Class<T> enumClass, Object... parameters) { this(enumClass, java.util.Arrays.asList(parameters)); }
    public EnumProxy(Class<T> enumClass, List<Object> parameters) { this.enumClass = enumClass; this.parameters = parameters; }
    public Object getParameter(int i) { return parameters.get(i); }
    public T getValue() {
        if (value == null) throw new IllegalStateException("Omniloader can't add new values to " + enumClass.getName() + " yet (enum extensions are not supported)");
        return value;
    }
    public void setValue(T v) { value = v; }
}
