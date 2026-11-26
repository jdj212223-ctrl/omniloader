package net.neoforged.fml;
import java.util.*; import java.util.concurrent.*; import java.util.function.*; import java.util.stream.*;
public class InterModComms {
    public record IMCMessage(String senderModId, String modId, String method, Supplier<?> messageSupplier) {
        @SuppressWarnings("unchecked") public <T> Supplier<T> getMessageSupplier() { return (Supplier<T>) messageSupplier; }
    }
    private static final Map<String, ConcurrentLinkedQueue<IMCMessage>> QUEUES = new ConcurrentHashMap<>();
    public static boolean sendTo(String modId, String method, Supplier<?> thing) {
        return sendTo(ModLoadingContext.get().getActiveNamespace(), modId, method, thing);
    }
    public static boolean sendTo(String sender, String modId, String method, Supplier<?> thing) {
        if (!ModList.get().isLoaded(modId)) return false;
        QUEUES.computeIfAbsent(modId, k -> new ConcurrentLinkedQueue<>()).add(new IMCMessage(sender, modId, method, thing));
        return true;
    }
    public static Stream<IMCMessage> getMessages(String modId, Predicate<String> methodMatcher) {
        ConcurrentLinkedQueue<IMCMessage> q = QUEUES.get(modId);
        if (q == null) return Stream.empty();
        List<IMCMessage> out = new ArrayList<>();
        q.removeIf(m -> { if (methodMatcher.test(m.method())) { out.add(m); return true; } return false; });
        return out.stream();
    }
    public static Stream<IMCMessage> getMessages(String modId) { return getMessages(modId, s -> true); }
}
