package net.neoforged.fml;
import net.neoforged.bus.api.*;
import net.neoforged.fml.config.*;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforgespi.language.IModInfo;
import java.util.*; import java.util.function.Supplier;
public abstract class ModContainer {
    protected final String modId, namespace;
    protected final IModInfo modInfo;
    private final Map<Class<? extends IExtensionPoint>, Supplier<?>> extensionPoints = new IdentityHashMap<>();
    public ModContainer(IModInfo info) { this.modInfo = info; this.modId = info.getModId(); this.namespace = info.getNamespace(); }
    public final String getModId() { return modId; }
    public final String getNamespace() { return namespace; }
    public IModInfo getModInfo() { return modInfo; }
    @SuppressWarnings("unchecked")
    public <T extends IExtensionPoint> Optional<T> getCustomExtension(Class<T> point) { Supplier<?> s = extensionPoints.get(point); return s == null ? Optional.empty() : Optional.ofNullable((T) s.get()); }
    public <T extends IExtensionPoint> void registerExtensionPoint(Class<T> point, T ext) { extensionPoints.put(point, () -> ext); }
    public <T extends IExtensionPoint> void registerExtensionPoint(Class<T> point, Supplier<T> ext) { extensionPoints.put(point, ext); }
    public void registerConfig(ModConfig.Type type, IConfigSpec spec) { ConfigTracker.INSTANCE.registerConfig(type, spec, this); }
    public void registerConfig(ModConfig.Type type, IConfigSpec spec, String fileName) { ConfigTracker.INSTANCE.registerConfig(type, spec, this, fileName); }
    public abstract IEventBus getEventBus();
    /** Builds the mod's code objects (its @Mod class); called once by the loader. */
    protected void constructMod() {}
    public final <T extends Event & IModBusEvent> void acceptEvent(T e) { acceptEvent(null, e); }
    public final <T extends Event & IModBusEvent> void acceptEvent(EventPriority phase, T e) {
        IEventBus bus = getEventBus();
        if (bus == null) return;
        ModLoadingContext ctx = ModLoadingContext.get();
        ModContainer prev = ctx.active();
        ctx.setActiveContainer(this);
        try { if (phase == null) bus.post(e); else bus.post(phase, e); }
        catch (Throwable t) {
            throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.errorduringevent", modId, e.getClass().getName()).withCause(t).withAffectedMod(modInfo));
        } finally { ctx.setActiveContainer(prev); }
    }
    @Override public String toString() { return "mod " + modId; }
}
