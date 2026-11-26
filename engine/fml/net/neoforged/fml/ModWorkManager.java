package net.neoforged.fml;
import java.util.concurrent.*;
/** Omniloader runs mod start-up work on the calling thread, one mod after another (simple and safe). */
public class ModWorkManager {
    static final ConcurrentLinkedQueue<Runnable> SYNC = new ConcurrentLinkedQueue<>();
    private static final Executor SYNC_EXEC = SYNC::add;
    private static final Executor PARALLEL = Runnable::run;
    public static Executor syncExecutor() { return SYNC_EXEC; }
    public static Executor parallelExecutor() { return PARALLEL; }
    static void drainSync() { Runnable r; while ((r = SYNC.poll()) != null) r.run(); }
}
