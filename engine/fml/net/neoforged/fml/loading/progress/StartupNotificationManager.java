package net.neoforged.fml.loading.progress;
import java.util.*; import java.util.function.Consumer;
public class StartupNotificationManager {
    private static final List<ProgressMeter> BARS = Collections.synchronizedList(new ArrayList<>());
    public record AgeMessage(long age, Message message) {}
    public static List<ProgressMeter> getCurrentProgress() { return List.copyOf(BARS); }
    public static ProgressMeter prependProgressBar(String name, int steps) { ProgressMeter m = new ProgressMeter(name, steps, 0, new Message(name, Message.MessageType.MOD)); BARS.add(0, m); return m; }
    public static ProgressMeter addProgressBar(String name, int steps) { ProgressMeter m = new ProgressMeter(name, steps, 0, new Message(name, Message.MessageType.MOD)); BARS.add(m); return m; }
    public static void popBar(ProgressMeter m) { BARS.remove(m); }
    public static List<AgeMessage> getMessages() { return List.of(); }
    public static void addModMessage(String s) {}
    public static void modLoaderMessage(String s) {}
    public static Optional<Consumer<String>> modLoaderConsumer() { return Optional.of(s -> {}); }
    public static Optional<Consumer<String>> locatorConsumer() { return Optional.of(s -> {}); }
    public static Optional<Consumer<String>> mcLoaderConsumer() { return Optional.of(s -> {}); }
}
