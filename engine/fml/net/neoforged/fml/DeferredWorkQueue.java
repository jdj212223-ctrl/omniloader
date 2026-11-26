package net.neoforged.fml;
import java.util.*; import java.util.concurrent.*; import java.util.function.Supplier;
public class DeferredWorkQueue {
    private final String name;
    private final ConcurrentLinkedDeque<Runnable> tasks = new ConcurrentLinkedDeque<>();
    public DeferredWorkQueue(String name) { this.name = name; }
    public void runTasks() { Runnable r; while ((r = tasks.poll()) != null) r.run(); }
    public CompletableFuture<Void> enqueueWork(ModContainer owner, Runnable work) {
        return enqueueWork(owner, () -> { work.run(); return null; });
    }
    public <T> CompletableFuture<T> enqueueWork(ModContainer owner, Supplier<T> work) {
        CompletableFuture<T> f = new CompletableFuture<>();
        tasks.add(() -> {
            ModLoadingContext.get().setActiveContainer(owner);
            try { f.complete(work.get()); }
            catch (Throwable t) { f.completeExceptionally(t); ModLoader.addLoadingIssue(ModLoadingIssue.error("fml.modloadingissue.technical_error", "Deferred work of " + owner.getModId() + " (" + name + ") failed").withCause(t).withAffectedMod(owner.getModInfo())); }
            finally { ModLoadingContext.get().setActiveContainer(null); }
        });
        return f;
    }
}
