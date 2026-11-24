package net.fabricmc.fabric.api.event;

import dev.omniloader.runtime.fabricapi.ArrayBackedEvent;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public final class EventFactory {
    private EventFactory() {}

    public static boolean isProfilingEnabled() { return false; }

    @Deprecated public static void invalidate() {}

    public static <T> Event<T> createArrayBacked(Class<? super T> type, Function<T[], T> invokerFactory) {
        return new ArrayBackedEvent<>(type, invokerFactory);
    }

    public static <T> Event<T> createArrayBacked(Class<T> type, T emptyInvoker, Function<T[], T> invokerFactory) {
        return new ArrayBackedEvent<>(type, listeners -> listeners.length == 0 ? emptyInvoker
                : listeners.length == 1 ? listeners[0] : invokerFactory.apply(listeners));
    }

    public static <T> Event<T> createWithPhases(Class<? super T> type, Function<T[], T> invokerFactory, ResourceLocation... defaultPhases) {
        ArrayBackedEvent<T> e = new ArrayBackedEvent<>(type, invokerFactory);
        for (int i = 1; i < defaultPhases.length; i++) e.addPhaseOrdering(defaultPhases[i - 1], defaultPhases[i]);
        return e;
    }
}
