package net.neoforged.fml;

import java.util.concurrent.*;

/**
 * Where mod start-up work runs. "Sync" work is queued for the thread that drives loading;
 * "parallel" work runs on Omniloader's background worker threads (NeoForge expects this: it waits for
 * main-thread work from a background thread during resource loading).
 */
public class ModWorkManager {
    static final ConcurrentLinkedQueue<Runnable> SYNC = new ConcurrentLinkedQueue<>();
    private static final Executor SYNC_EXEC = SYNC::add;
    private static final ClassLoader LOADER = Thread.currentThread().getContextClassLoader();
    private static final ForkJoinPool PARALLEL = new ForkJoinPool(Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors() - 1)), pool -> {
        ForkJoinWorkerThread t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        t.setName("Omniloader mod loading " + t.getPoolIndex());
        t.setDaemon(true);
        t.setContextClassLoader(Thread.currentThread().getContextClassLoader() != null ? Thread.currentThread().getContextClassLoader() : LOADER);
        return t;
    }, null, false);

    public static Executor syncExecutor() { return SYNC_EXEC; }
    public static Executor parallelExecutor() { return PARALLEL; }

    static boolean onWorker() { return Thread.currentThread() instanceof ForkJoinWorkerThread w && w.getPool() == PARALLEL; }

    static void drainSync() {
        if (onWorker()) return; // queued main-thread work must not run on a background thread
        Runnable r;
        while ((r = SYNC.poll()) != null) r.run();
    }
}
