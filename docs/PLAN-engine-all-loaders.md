# Plan: Omniloader's own engine runs Fabric, Forge and NeoForge mods (no FML)

Status: engine milestone 1 done (vanilla 1.21.1 + Fabric mods, commit 7e4e7e5). Shared mod finder split out
(ModsFolderScan, NestedJars) so the engine and the NeoForge edition use one copy.

## Idea
Omniloader is the loader. It writes its OWN versions of the loader-side names (`net.neoforged.fml.*`,
`net.neoforged.neoforgespi.*`, a few `cpw.mods.*` interfaces): no FML / ModLauncher code is used.
NeoForge's API + patched game ride on top as libraries; Forge mods (Forge bridge) and Fabric mods
(bundled Forgified Fabric API + runtime) already run on NeoForge's API, so all three come along.

## What Omniloader's loader must provide (measured from NeoForge 21.1.264, FFAPI, runtime, test mods)
~60 classes / ~150 methods. Key ones: ModLoader (gatherAndInitializeMods, dispatchParallelEvent, runInitTask,
postEvent*), ModList, LoadingModList, ModContainer + FMLModContainer (@Mod ctor injection: IEventBus,
ModContainer, Dist), EventBusSubscriber auto-registration, ModLoadingContext, config (ConfigTracker,
ModConfig, ModConfigs, ModConfigEvent), lifecycle events + DeferredWorkQueue, FMLLoader/FMLEnvironment/
FMLPaths, ImmediateWindowHandler (create window normally, vanilla LoadingOverlay), BackgroundWaiter,
VersionChecker (no-op), IModInfo/IModFileInfo/IModFile/ModFileScanData (ASM annotation scan), SecureJar
(getRootPath), ICoreMod + ITransformer (run NeoForge's coremods), SidedThreadGroups/EffectiveSide,
TopologicalSort, CrashReportCallables, StartupNotificationManager (no-op), ExtensionInfo.nonExtended.

## Engine "full" mode start-up
1. Class loader: NeoForge patched classes (neoforge-21.1.264-client.jar) before Mojang-named game
   (client-1.21.1-20240808.144430-srg.jar) + client-extra (assets).
2. Mods: neoforge universal (+ jarjar: coremods, mixinextras), Omniloader runtime + bundled FFAPI,
   mods folder via ModsFolderScan + ReaderCore (Fabric/Quilt/Forge converted), jarjar nested jars.
3. Parse neoforge.mods.toml (night-config), sort by dependencies, scan annotations.
4. Class changes in order: access transformers (AccessRules: add `default`, `*`, `*()`) -> coremods -> Mixin
   (sponge-mixin 0.16.4 + MixinExtras init).
5. Start net.minecraft.client.main.Main; NeoForge's own ClientModLoader drives ModLoader from there.
Libraries: NeoForge's list minus FML, modlauncher, bootstraplauncher, securejarhandler, coremods (JS),
accesstransformers, JarJar*, earlydisplay, nashorn.

## Later
Enum extensions (RuntimeEnumExtender), server side, launcher installs of the engine.
