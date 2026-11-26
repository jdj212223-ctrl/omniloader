package net.neoforged.fml.event.lifecycle;
import net.neoforged.fml.*;
public class InterModEnqueueEvent extends ParallelDispatchEvent {
    public InterModEnqueueEvent(ModContainer container, DeferredWorkQueue queue) { super(container, queue); }
}
