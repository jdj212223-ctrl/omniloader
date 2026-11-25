package dev.omniloader.runtime;

import dev.omniloader.runtime.fabric.FabricLoaderImpl;
import dev.omniloader.runtime.fabric.ModContainerImpl;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Sits in front of every converted Fabric/Quilt mod's mixins. If Omniloader switched the mod off
 * (something it needs is missing), none of its mixins apply. Otherwise it passes everything through
 * to the mod's own mixin plugin, if it has one.
 */
public final class OmniMixinPlugin implements IMixinConfigPlugin {
    private ModContainerImpl mod;
    private IMixinConfigPlugin original;

    @Override
    public void onLoad(String mixinPackage) {
        FabricLoaderImpl.MixinOwner owner = FabricLoaderImpl.INSTANCE.mixinOwner(mixinPackage);
        if (owner == null) return;
        mod = owner.mod();
        if (owner.plugin() != null && mod.active) {
            try {
                original = (IMixinConfigPlugin) Class.forName(owner.plugin(), true, OmniMixinPlugin.class.getClassLoader())
                        .getDeclaredConstructor().newInstance();
                original.onLoad(mixinPackage);
            } catch (Throwable e) {
                // One mod's broken plugin must not take the whole game down: switch that mod off instead.
                original = null;
                Throwable root = e;
                while (root.getCause() != null) root = root.getCause();
                FabricLoaderImpl.INSTANCE.switchOff(mod, "its mixin plugin (" + owner.plugin() + ") failed to start: " + root
                        + (String.valueOf(root).contains("delegate") || String.valueOf(root).contains("Knot")
                        ? " - it reaches into Fabric Loader's internals, which Omniloader doesn't copy. Try the mod's NeoForge version." : ""));
            }
        }
    }

    private boolean off() { return mod != null && !mod.active; }

    @Override public String getRefMapperConfig() { return original != null ? original.getRefMapperConfig() : null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (off()) return false;
        try {
            return original == null || original.shouldApplyMixin(targetClassName, mixinClassName);
        } catch (Throwable t) {
            FabricLoaderImpl.INSTANCE.switchOff(mod, "its mixin plugin crashed: " + t);
            return false;
        }
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { if (original != null) original.acceptTargets(myTargets, otherTargets); }
    @Override public List<String> getMixins() { return original != null && !off() ? original.getMixins() : null; }
    @Override public void preApply(String t, ClassNode n, String m, IMixinInfo i) { if (original != null) original.preApply(t, n, m, i); }
    @Override public void postApply(String t, ClassNode n, String m, IMixinInfo i) { if (original != null) original.postApply(t, n, m, i); }
}
