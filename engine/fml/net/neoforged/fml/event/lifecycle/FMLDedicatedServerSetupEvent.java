package net.neoforged.fml.event.lifecycle;
import net.neoforged.fml.*;
public class FMLDedicatedServerSetupEvent extends ParallelDispatchEvent {
    public FMLDedicatedServerSetupEvent(ModContainer container, DeferredWorkQueue queue) { super(container, queue); }
}
