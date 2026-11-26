package net.neoforged.fml.event.lifecycle;
import net.neoforged.fml.*;
public class FMLClientSetupEvent extends ParallelDispatchEvent {
    public FMLClientSetupEvent(ModContainer container, DeferredWorkQueue queue) { super(container, queue); }
}
