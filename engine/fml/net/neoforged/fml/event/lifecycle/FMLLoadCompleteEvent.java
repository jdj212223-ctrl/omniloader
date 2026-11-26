package net.neoforged.fml.event.lifecycle;
import net.neoforged.fml.*;
public class FMLLoadCompleteEvent extends ParallelDispatchEvent {
    public FMLLoadCompleteEvent(ModContainer container, DeferredWorkQueue queue) { super(container, queue); }
}
