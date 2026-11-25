package dev.omniloader.engine.mixin;

import org.spongepowered.asm.service.IMixinServiceBootstrap;

public final class OmniMixinBootstrap implements IMixinServiceBootstrap {
    @Override public String getName() { return "Omniloader"; }
    @Override public String getServiceClassName() { return OmniMixinService.class.getName(); }
    @Override public void bootstrap() {}
}
