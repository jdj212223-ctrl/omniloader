package net.neoforged.fml.loading.moddiscovery;
public final class ModAnnotation {
    private ModAnnotation() {}
    public record EnumHolder(String desc, String value) {
        public String getDesc() { return desc; }
        public String getValue() { return value; }
    }
}
