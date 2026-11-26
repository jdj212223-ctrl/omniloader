package net.neoforged.fml;
public interface ISystemReportExtender extends java.util.function.Supplier<String> {
    String getLabel();
    default boolean isActive() { return true; }
}
