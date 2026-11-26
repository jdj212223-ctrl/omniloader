package dev.omniloader.runtime.mixin;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.internal.BrandingControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/** Omniloader shows its own name in the corner of the title screen, above NeoForge's engine line. */
@Mixin(value = BrandingControl.class, remap = false)
public abstract class BrandingControlMixin {
    @Shadow private static List<String> brandings;
    @Shadow private static List<String> brandingsNoMC;

    @Inject(method = "computeBranding", at = @At("TAIL"))
    private static void omniloader$brand(CallbackInfo ci) {
        if (brandings == null || brandings.stream().anyMatch(s -> s.startsWith("Omniloader"))) return;
        String version = ModList.get().getModContainerById("omniloader").map(c -> c.getModInfo().getVersion().toString()).orElse("");
        List<String> b = new ArrayList<>(brandings);
        b.add(1, "Omniloader " + version + " (Fabric, Quilt, Forge, NeoForge)");
        brandings = List.copyOf(b);
        brandingsNoMC = brandings.subList(1, brandings.size());
    }
}
