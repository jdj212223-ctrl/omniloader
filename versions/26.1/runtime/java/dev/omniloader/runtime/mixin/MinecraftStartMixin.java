package dev.omniloader.runtime.mixin;

import net.minecraft.client.Minecraft;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On 26.1 NeoForge loads mods before the game object exists. Fabric mods' client code expects it to exist
 * (with its folder and player profile), so start that code where Fabric itself does: early in the game's constructor.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftStartMixin {
    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;user:Lnet/minecraft/client/User;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void omniloader$startFabricClientCode(CallbackInfo ci) {
        dev.omniloader.runtime.OmniloaderMod.startClient();
    }
}
