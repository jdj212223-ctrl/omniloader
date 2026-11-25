package dev.omniloader.engine.mixin;

import dev.omniloader.engine.Engine;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.service.MixinServiceAbstract;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.List;

/** Omniloader's connection to Mixin: tells Mixin where classes come from, and receives Mixin's class changer. */
public final class OmniMixinService extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider {
    private static volatile IMixinTransformer transformer;

    public static IMixinTransformer transformer() { return transformer; }

    @Override public String getName() { return "Omniloader"; }
    @Override public boolean isValid() { return true; }
    @Override public IClassProvider getClassProvider() { return this; }
    @Override public IClassBytecodeProvider getBytecodeProvider() { return this; }
    @Override public ITransformerProvider getTransformerProvider() { return null; }
    @Override public IClassTracker getClassTracker() { return null; }
    @Override public IMixinAuditTrail getAuditTrail() { return null; }
    @Override public Collection<String> getPlatformAgents() { return List.of("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault"); }
    @Override public IContainerHandle getPrimaryContainer() { return new ContainerHandleVirtual(getName()); }
    @Override public InputStream getResourceAsStream(String name) { return Engine.loader.getResourceAsStream(name); }
    @Override public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() { return MixinEnvironment.CompatibilityLevel.JAVA_21; }

    @Override
    public void offer(IMixinInternal internal) {
        if (internal instanceof IMixinTransformerFactory f) transformer = f.createTransformer();
        super.offer(internal);
    }

    // ---- where classes come from
    @Override @Deprecated public URL[] getClassPath() { return new URL[0]; }
    @Override public Class<?> findClass(String name) throws ClassNotFoundException { return Engine.loader.loadClass(name); }
    @Override public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException { return Class.forName(name, initialize, Engine.loader); }
    @Override public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException { return Class.forName(name, initialize, OmniMixinService.class.getClassLoader()); }

    // ---- class files, as Mixin wants to see them (before mixins, after mods' access changes)
    @Override public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException { return getClassNode(name, true); }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException { return getClassNode(name, runTransformers, ClassReader.EXPAND_FRAMES); }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags) throws ClassNotFoundException, IOException {
        String binary = name.replace('/', '.');
        byte[] b = Engine.loader.rawBytes(binary);
        if (b == null) throw new ClassNotFoundException(name);
        if (Engine.access != null) b = Engine.access.transform(binary.replace('.', '/'), b);
        ClassNode node = new ClassNode();
        new ClassReader(b).accept(node, readerFlags);
        return node;
    }
}
