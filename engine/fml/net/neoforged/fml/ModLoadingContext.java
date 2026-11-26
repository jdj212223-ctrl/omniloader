package net.neoforged.fml;
import java.util.function.Supplier;
public class ModLoadingContext {
    private static final ThreadLocal<ModLoadingContext> CONTEXT = ThreadLocal.withInitial(ModLoadingContext::new);
    private ModContainer active;
    public static ModLoadingContext get() { return CONTEXT.get(); }
    public void setActiveContainer(ModContainer c) { active = c; }
    ModContainer active() { return active; }
    public ModContainer getActiveContainer() { return active != null ? active : ModList.get().getModContainerById("minecraft").orElse(null); }
    public String getActiveNamespace() { return active == null ? "minecraft" : active.getNamespace(); }
    public <T extends IExtensionPoint> void registerExtensionPoint(Class<T> point, Supplier<T> ext) { getActiveContainer().registerExtensionPoint(point, ext); }
}
