package net.neoforged.fml.event.lifecycle;
import net.neoforged.fml.*;
public class FMLConstructModEvent extends ParallelDispatchEvent {
    public FMLConstructModEvent(ModContainer container, DeferredWorkQueue queue) { super(container, queue); }
}
