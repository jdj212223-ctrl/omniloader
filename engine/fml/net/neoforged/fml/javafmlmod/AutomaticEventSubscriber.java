package net.neoforged.fml.javafmlmod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.*;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.moddiscovery.ModAnnotation;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.Type;
import java.lang.annotation.ElementType;
import java.util.*;
/** Registers classes marked @EventBusSubscriber on the right event bus. */
public final class AutomaticEventSubscriber {
    private static final Type SUB = Type.getType("Lnet/neoforged/fml/common/EventBusSubscriber;");
    private static final Type MOD = Type.getType("Lnet/neoforged/fml/common/Mod;");
    private AutomaticEventSubscriber() {}

    public static EnumSet<Dist> getSides(Object data) { return sidesOf(data); }

    static EnumSet<Dist> sidesOf(Object data) {
        if (!(data instanceof List<?> l) || l.isEmpty()) return EnumSet.allOf(Dist.class);
        EnumSet<Dist> out = EnumSet.noneOf(Dist.class);
        for (Object o : l) out.add(Dist.valueOf(o instanceof ModAnnotation.EnumHolder h ? h.value() : String.valueOf(o)));
        return out;
    }

    static void inject(ModContainer mod, ModFileScanData scan, ClassLoader loader) {
        List<ModFileScanData.AnnotationData> mods = scan.getAnnotations().stream().filter(a -> a.annotationType().equals(MOD)).toList();
        for (ModFileScanData.AnnotationData a : scan.getAnnotations()) {
            if (a.targetType() != ElementType.TYPE || !a.annotationType().equals(SUB)) continue;
            String owner = (String) a.annotationData().get("modid");
            if (owner == null || owner.isEmpty()) {
                // same class or its outer class carries @Mod? otherwise: the file's single mod
                owner = mods.stream().filter(m -> a.clazz().getClassName().startsWith(m.clazz().getClassName())).map(m -> (String) m.annotationData().get("value")).findFirst()
                        .orElse(scan.getIModInfoData().isEmpty() || scan.getIModInfoData().get(0).getMods().isEmpty() ? null : scan.getIModInfoData().get(0).getMods().get(0).getModId());
            }
            if (!mod.getModId().equals(owner)) continue;
            if (!sidesOf(a.annotationData().get("value")).contains(FMLLoader.getDist())) continue;
            Object busData = a.annotationData().get("bus");
            boolean modBus = busData instanceof ModAnnotation.EnumHolder h && h.value().equals("MOD");
            try {
                Class<?> c = Class.forName(a.clazz().getClassName(), true, loader);
                if (modBus) mod.getEventBus().register(c);
                else FMLLoader.getBindings().getGameBus().register(c);
            } catch (Throwable t) {
                throw new ModLoadingException(ModLoadingIssue.error("fml.modloadingissue.failedtoloadmod", mod.getModId()).withCause(t).withAffectedMod(mod.getModInfo()));
            }
        }
    }
}
