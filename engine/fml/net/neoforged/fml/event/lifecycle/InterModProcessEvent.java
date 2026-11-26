package net.neoforged.fml.event.lifecycle;
import net.neoforged.fml.*;
public class InterModProcessEvent extends ParallelDispatchEvent {
    public InterModProcessEvent(ModContainer container, DeferredWorkQueue queue) { super(container, queue); }
}
