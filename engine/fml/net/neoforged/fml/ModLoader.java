package net.neoforged.fml;
import net.neoforged.bus.api.*;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.event.lifecycle.*;
import net.neoforged.fml.loading.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.*; import java.util.concurrent.*; import java.util.function.*;
/** Omniloader's mod start-up: builds every mod, then walks them through NeoForge's start-up steps. */
public final class ModLoader {
    private static final Logger LOG = LogManager.getLogger("Omniloader");
    private static final List<ModLoadingIssue> ISSUES = new CopyOnWriteArrayList<>();
    private static Function<net.neoforged.fml.loading.moddiscovery.ModInfo, ModContainer> containerFactory;
    private ModLoader() {}

    /** Set by Omniloader's engine: how a mod container is made for a mod. */
    public static void setContainerFactory(Function<net.neoforged.fml.loading.moddiscovery.ModInfo, ModContainer> f) { containerFactory = f; }

    public static void gatherAndInitializeMods(Executor syncExecutor, Executor parallelExecutor, Runnable periodicTask) {
        LoadingModList lml = LoadingModList.get();
        ISSUES.addAll(lml.getModLoadingIssues());
        List<ModContainer> containers = new ArrayList<>();
        for (var mod : lml.getMods()) {
            try { containers.add(containerFactory.apply(mod)); }
            catch (Throwable t) { addLoadingIssue(ModLoadingIssue.error("fml.modloadingissue.failedtoloadmod", mod.getModId()).withCause(t).withAffectedMod(mod)); }
        }
        ModList.get().setLoadedMods(containers);
        if (hasErrors()) throw new ModLoadingException(getLoadingErrors());
        for (ModContainer c : containers) {
            LOG.info("[Omniloader] Starting mod {}", c.getModId());
            try { c.constructMod(); }
            catch (ModLoadingException e) { ISSUES.addAll(e.getIssues()); }
            catch (Throwable t) { addLoadingIssue(ModLoadingIssue.error("fml.modloadingissue.failedtoloadmod", c.getModId()).withCause(t).withAffectedMod(c.getModInfo())); }
            periodicTask.run();
        }
        if (hasErrors()) throw new ModLoadingException(getLoadingErrors());
        dispatchParallelEvent("construct", syncExecutor, parallelExecutor, periodicTask, FMLConstructModEvent::new);
    }

    public static void runInitTask(String name, Executor syncExecutor, Runnable periodicTask, Runnable task) {
        waitForTask(name, periodicTask, CompletableFuture.runAsync(task, syncExecutor));
    }

    public static void dispatchParallelEvent(String name, Executor syncExecutor, Executor parallelExecutor, Runnable periodicTask, BiFunction<ModContainer, DeferredWorkQueue, ParallelDispatchEvent> eventConstructor) {
        DeferredWorkQueue queue = new DeferredWorkQueue(name);
        dispatchParallelTask(name, parallelExecutor, periodicTask, c -> c.acceptEvent(eventConstructor.apply(c, queue)));
        runInitTask(name, syncExecutor, periodicTask, queue::runTasks);
    }

    public static void waitForTask(String name, Runnable periodicTask, CompletableFuture<?> future) {
        while (!future.isDone()) {
            ModWorkManager.drainSync();
            periodicTask.run();
            if (!future.isDone()) try { future.get(10, TimeUnit.MILLISECONDS); } catch (TimeoutException ignored) {} catch (Exception e) { break; }
        }
        ModWorkManager.drainSync();
        try { future.join(); }
        catch (CompletionException e) {
            Throwable c = e.getCause();
            if (c instanceof ModLoadingException mle) { ISSUES.addAll(mle.getIssues()); }
            else addLoadingIssue(ModLoadingIssue.error("fml.modloadingissue.technical_error", name + " failed").withCause(c));
        }
        if (hasErrors()) throw new ModLoadingException(getLoadingErrors());
    }

    public static void dispatchParallelTask(String name, Executor parallelExecutor, Runnable periodicTask, Consumer<ModContainer> task) {
        List<CompletableFuture<Void>> all = new ArrayList<>();
        for (ModContainer c : ModList.get().getSortedMods()) {
            all.add(CompletableFuture.runAsync(() -> {
                try { task.accept(c); }
                catch (ModLoadingException e) { ISSUES.addAll(e.getIssues()); }
                catch (Throwable t) { addLoadingIssue(ModLoadingIssue.error("fml.modloadingissue.errorduringevent", c.getModId(), name).withCause(t).withAffectedMod(c.getModInfo())); }
            }, parallelExecutor));
        }
        waitForTask(name, periodicTask, CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)));
    }

    public static <T extends Event & IModBusEvent> void runEventGenerator(Function<ModContainer, T> generator) {
        for (ModContainer c : ModList.get().getSortedMods()) c.acceptEvent(generator.apply(c));
    }

    public static <T extends Event & IModBusEvent> void postEvent(T e) {
        for (EventPriority p : EventPriority.values()) for (ModContainer c : ModList.get().getSortedMods()) c.acceptEvent(p, e);
    }

    public static <T extends Event & IModBusEvent> T postEventWithReturn(T e) { postEvent(e); return e; }

    public static <T extends Event & IModBusEvent> void postEventWrapContainerInModOrder(T e) { postEventWithWrapInModOrder(e, (c, ev) -> {}, (c, ev) -> {}); }

    public static <T extends Event & IModBusEvent> void postEventWithWrapInModOrder(T e, BiConsumer<ModContainer, T> pre, BiConsumer<ModContainer, T> post) {
        for (EventPriority p : EventPriority.values()) for (ModContainer c : ModList.get().getSortedMods()) {
            pre.accept(c, e);
            c.acceptEvent(p, e);
            post.accept(c, e);
        }
    }

    public static boolean hasErrors() { return ISSUES.stream().anyMatch(i -> i.severity() == ModLoadingIssue.Severity.ERROR); }
    public static List<ModLoadingIssue> getLoadingErrors() { return ISSUES.stream().filter(i -> i.severity() == ModLoadingIssue.Severity.ERROR).toList(); }
    public static List<ModLoadingIssue> getLoadingWarnings() { return ISSUES.stream().filter(i -> i.severity() == ModLoadingIssue.Severity.WARNING).toList(); }
    public static List<ModLoadingIssue> getLoadingIssues() { return List.copyOf(ISSUES); }
    public static void clearLoadingIssues() { ISSUES.clear(); }
    public static void addLoadingIssue(ModLoadingIssue i) {
        ISSUES.add(i);
        if (i.cause() != null) LOG.error("[Omniloader] {}", i, i.cause()); else LOG.warn("[Omniloader] {}", i);
    }
}
