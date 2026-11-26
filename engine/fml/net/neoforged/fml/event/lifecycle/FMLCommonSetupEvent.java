package net.neoforged.fml.event.lifecycle;
import net.neoforged.fml.*;
public class FMLCommonSetupEvent extends ParallelDispatchEvent {
    public FMLCommonSetupEvent(ModContainer container, DeferredWorkQueue queue) { super(container, queue); }
}
