package net.neoforged.fml.event.lifecycle;
import net.neoforged.fml.*;
import java.util.concurrent.CompletableFuture; import java.util.function.Supplier;
public abstract class ParallelDispatchEvent extends ModLifecycleEvent {
    private final DeferredWorkQueue queue;
    public ParallelDispatchEvent(ModContainer container, DeferredWorkQueue queue) { super(container); this.queue = queue; }
    public CompletableFuture<Void> enqueueWork(Runnable work) { return queue.enqueueWork(getContainer(), work); }
    public <T> CompletableFuture<T> enqueueWork(Supplier<T> work) { return queue.enqueueWork(getContainer(), work); }
}
