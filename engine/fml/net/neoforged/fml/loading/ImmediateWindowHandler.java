package net.neoforged.fml.loading;
import java.util.Optional; import java.util.function.*;
/** NeoForge can show its own loading window before Minecraft's. Omniloader lets Minecraft open its normal window instead. */
public class ImmediateWindowHandler {
    public static void load(String launchTarget, String[] args) {}
    public static long setupMinecraftWindow(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor) {
        return org.lwjgl.glfw.GLFW.glfwCreateWindow(width.getAsInt(), height.getAsInt(), title.get(), monitor.getAsLong(), 0L);
    }
    public static boolean positionWindow(Optional<Object> monitor, IntConsumer w, IntConsumer h, IntConsumer x, IntConsumer y) { return false; }
    public static void updateFBSize(IntConsumer width, IntConsumer height) {}
    @SuppressWarnings("unchecked")
    public static <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> reload, Consumer<Optional<Throwable>> errorConsumer, boolean fadeIn) {
        return () -> {
            try {
                Object minecraft = mc.get();
                ClassLoader cl = minecraft.getClass().getClassLoader();
                Class<?> overlay = Class.forName("net.minecraft.client.gui.screens.LoadingOverlay", true, cl);
                Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft", false, cl);
                Class<?> ri = Class.forName("net.minecraft.server.packs.resources.ReloadInstance", false, cl);
                return (T) overlay.getConstructor(mcClass, ri, Consumer.class, boolean.class).newInstance(minecraft, reload.get(), errorConsumer, fadeIn);
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        };
    }
    public static void acceptGameLayer(ModuleLayer layer) {}
    public static void renderTick() {}
    public static String getGLVersion() { return "3.2"; }
    public static void updateProgress(String message) {}
    public static void crash(String message) {}
}
