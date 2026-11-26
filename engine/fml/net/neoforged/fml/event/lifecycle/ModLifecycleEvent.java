package net.neoforged.fml.event.lifecycle;
import net.neoforged.bus.api.Event; import net.neoforged.fml.*; import net.neoforged.fml.event.IModBusEvent;
import java.util.function.Predicate; import java.util.stream.Stream;
public abstract class ModLifecycleEvent extends Event implements IModBusEvent {
    private final ModContainer container;
    public ModLifecycleEvent(ModContainer container) { this.container = container; }
    public final String description() { String n = getClass().getSimpleName(); return n.replaceAll("([A-Z])", " $1").trim(); }
    public Stream<InterModComms.IMCMessage> getIMCStream() { return InterModComms.getMessages(container.getModId()); }
    public Stream<InterModComms.IMCMessage> getIMCStream(Predicate<String> method) { return InterModComms.getMessages(container.getModId(), method); }
    public ModContainer getContainer() { return container; }
    @Override public String toString() { return description(); }
}
