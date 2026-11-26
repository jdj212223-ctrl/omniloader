package net.neoforged.neoforgespi.coremod;
public interface ICoreMod {
    Iterable<? extends cpw.mods.modlauncher.api.ITransformer<?>> getTransformers();
}
