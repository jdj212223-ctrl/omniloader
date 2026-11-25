package net.minecraftforge.fml;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/** Forge's "run this only on the client/server" helper (Omniloader stand-in). */
public final class DistExecutor {
    private DistExecutor() {}

    private static boolean on(Dist d) { return FMLEnvironment.dist == d; }

    public static <T> T callWhenOn(Dist dist, Supplier<Callable<T>> toRun) { return unsafeCallWhenOn(dist, toRun); }
    public static <T> T unsafeCallWhenOn(Dist dist, Supplier<Callable<T>> toRun) {
        if (!on(dist)) return null;
        try { return toRun.get().call(); } catch (Exception e) { throw new RuntimeException(e); }
    }
    public static <T> T safeCallWhenOn(Dist dist, Supplier<SafeCallable<T>> toRun) { return unsafeCallWhenOn(dist, () -> toRun.get()); }
    public static void runWhenOn(Dist dist, Supplier<Runnable> toRun) { unsafeRunWhenOn(dist, toRun); }
    public static void unsafeRunWhenOn(Dist dist, Supplier<Runnable> toRun) { if (on(dist)) toRun.get().run(); }
    public static void safeRunWhenOn(Dist dist, Supplier<SafeRunnable> toRun) { if (on(dist)) toRun.get().run(); }
    public static <T> T runForDist(Supplier<Supplier<T>> clientTarget, Supplier<Supplier<T>> serverTarget) { return unsafeRunForDist(clientTarget, serverTarget); }
    public static <T> T unsafeRunForDist(Supplier<Supplier<T>> clientTarget, Supplier<Supplier<T>> serverTarget) {
        return (FMLEnvironment.dist.isClient() ? clientTarget : serverTarget).get().get();
    }
    public static <T> T safeRunForDist(Supplier<SafeSupplier<T>> clientTarget, Supplier<SafeSupplier<T>> serverTarget) {
        return (FMLEnvironment.dist.isClient() ? clientTarget : serverTarget).get().get();
    }

    public interface SafeReferent {}
    public interface SafeCallable<T> extends SafeReferent, Callable<T>, Serializable {}
    public interface SafeSupplier<T> extends SafeReferent, Supplier<T>, Serializable {}
    public interface SafeRunnable extends SafeReferent, Runnable, Serializable {}
}
