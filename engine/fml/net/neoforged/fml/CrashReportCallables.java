package net.neoforged.fml;
import java.util.*; import java.util.function.*; import java.util.stream.Stream;
public class CrashReportCallables {
    private static final List<ISystemReportExtender> CALLABLES = Collections.synchronizedList(new ArrayList<>());
    private static final List<ICrashReportHeader> HEADERS = Collections.synchronizedList(new ArrayList<>());
    public static void registerCrashCallable(ISystemReportExtender e) { CALLABLES.add(e); }
    public static void registerCrashCallable(String label, Supplier<String> s) { registerCrashCallable(label, s, () -> true); }
    public static void registerCrashCallable(String label, Supplier<String> s, BooleanSupplier active) {
        registerCrashCallable(new ISystemReportExtender() {
            public String getLabel() { return label; }
            public String get() { return s.get(); }
            public boolean isActive() { return active.getAsBoolean(); }
        });
    }
    public static void registerHeader(ICrashReportHeader h) { HEADERS.add(h); }
    public static List<ISystemReportExtender> allCrashCallables() { return List.copyOf(CALLABLES); }
    public static Stream<String> getHeaders() { return List.copyOf(HEADERS).stream().map(ICrashReportHeader::getHeader); }
}
