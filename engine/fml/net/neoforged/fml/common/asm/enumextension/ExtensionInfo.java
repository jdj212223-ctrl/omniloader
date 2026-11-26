package net.neoforged.fml.common.asm.enumextension;
public record ExtensionInfo(boolean extended, int vanillaCount, int totalCount, NetworkedEnum.NetworkCheck netCheck) {
    public static <T extends Enum<T> & IExtensibleEnum> ExtensionInfo nonExtended(Class<T> e) {
        int n = e.getEnumConstants().length;
        NetworkedEnum ne = e.getAnnotation(NetworkedEnum.class);
        return new ExtensionInfo(false, n, n, ne == null ? null : ne.value());
    }
}
