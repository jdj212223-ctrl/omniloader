package net.neoforged.fml.javafmlmod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.*;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.language.*;
import org.objectweb.asm.Type;
import java.lang.annotation.ElementType;
import java.lang.reflect.Constructor;
import java.util.*;
/** A Java mod: builds its @Mod classes and hooks up its @EventBusSubscriber classes. */
public class FMLModContainer extends ModContainer {
    private static final Type MOD = Type.getType("Lnet/neoforged/fml/common/Mod;");
    private final IEventBus bus;
    private final ModFileScanData scan;
    private final ClassLoader loader;
    private final List<Object> instances = new ArrayList<>();

    public FMLModContainer(IModInfo info, ModFileScanData scan, ClassLoader loader) {
        super(info);
        this.scan = scan;
        this.loader = loader;
        this.bus = BusBuilder.builder()
                .setExceptionHandler((b, event, listeners, i, t) -> org.apache.logging.log4j.LogManager.getLogger("Omniloader").error("Exception in {} while handling {}", modId, event.getClass().getName(), t))
                .markerType(IModBusEvent.class).allowPerPhasePost().build();
    }

    public List<Object> instances() { return instances; }
    @Override public IEventBus getEventBus() { return bus; }

    @Override protected void constructMod() {
        Dist dist = FMLLoader.getDist();
        List<String> classes = new ArrayList<>();
        for (ModFileScanData.AnnotationData a : scan.getAnnotations()) {
            if (a.targetType() != ElementType.TYPE || !a.annotationType().equals(MOD)) continue;
            if (!modId.equals(a.annotationData().get("value"))) continue;
            if (!AutomaticEventSubscriber.sidesOf(a.annotationData().get("dist")).contains(dist)) continue;
            classes.add(a.clazz().getClassName());
        }
        for (String cn : classes) {
            try {
                Class<?> c = Class.forName(cn, true, loader);
                instances.add(construct(c, dist));
            } catch (Throwable t) {
                Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite ? ite.getCause() : t;
                throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.failedtoloadmod", modId).withCause(cause).withAffectedMod(modInfo));
            }
        }
        AutomaticEventSubscriber.inject(this, scan, loader);
    }

    private Object construct(Class<?> c, Dist dist) throws Exception {
        Constructor<?>[] ctors = c.getDeclaredConstructors();
        if (ctors.length != 1) {
            // pick the public one with the most parameters we can fill
            Arrays.sort(ctors, Comparator.comparingInt(Constructor::getParameterCount));
        }
        Constructor<?> ctor = ctors[ctors.length - 1];
        Object[] args = new Object[ctor.getParameterCount()];
        Class<?>[] types = ctor.getParameterTypes();
        for (int i = 0; i < args.length; i++) {
            Class<?> t = types[i];
            if (t == IEventBus.class) args[i] = bus;
            else if (t.isAssignableFrom(FMLModContainer.class)) args[i] = this;
            else if (t == Dist.class) args[i] = dist;
            else throw new IllegalStateException(c.getName() + "'s constructor asks for " + t.getName() + ", which mods can't receive");
        }
        ctor.setAccessible(true);
        ModLoadingContext.get().setActiveContainer(this);
        try { return ctor.newInstance(args); } finally { ModLoadingContext.get().setActiveContainer(null); }
    }
}
