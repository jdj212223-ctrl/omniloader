package net.neoforged.fml.loading;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.moddiscovery.*;
import net.neoforged.neoforgespi.language.*;
import java.net.URL; import java.nio.file.*; import java.util.*;
public class LoadingModList {
    private static LoadingModList INSTANCE;
    private final List<ModFileInfo> files;
    private final List<ModInfo> mods;
    private final List<ModLoadingIssue> issues;
    private final Map<String, ModFileInfo> byId = new HashMap<>();
    private LoadingModList(List<ModFileInfo> files, List<ModInfo> mods, List<ModLoadingIssue> issues) {
        this.files = files; this.mods = mods; this.issues = issues;
        for (ModInfo m : mods) byId.put(m.getModId(), m.getOwningFile());
    }
    public static LoadingModList create(List<ModFileInfo> files, List<ModInfo> sortedMods, List<ModLoadingIssue> issues) {
        INSTANCE = new LoadingModList(files, sortedMods, issues);
        FMLLoader.setLoadingModList(INSTANCE);
        return INSTANCE;
    }
    public static LoadingModList get() { return INSTANCE; }
    public void addMixinConfigs() {}
    public void addAccessTransformers() {}
    public void addEnumExtenders() {}
    public List<IModFileInfo> getPlugins() { return List.of(); }
    public List<ModFileInfo> getModFiles() { return files; }
    public Path findResource(String name) {
        for (ModFileInfo f : files) { Path p = f.getFile().findResource(name); if (Files.exists(p)) return p; }
        return null;
    }
    public Enumeration<URL> findAllURLsForResource(String name) {
        List<URL> out = new ArrayList<>();
        for (ModFileInfo f : files) {
            Path p = f.getFile().findResource(name);
            if (Files.exists(p)) try { out.add(p.toUri().toURL()); } catch (Exception ignored) {}
        }
        return Collections.enumeration(out);
    }
    public ModFileInfo getModFileById(String id) { return byId.get(id); }
    public List<ModInfo> getMods() { return mods; }
    public List<ModInfo> getDependencies(IModInfo mod) {
        List<ModInfo> out = new ArrayList<>();
        for (var d : mod.getDependencies()) for (ModInfo m : mods) if (m.getModId().equals(d.getModId())) out.add(m);
        return out;
    }
    public boolean hasErrors() { return issues.stream().anyMatch(i -> i.severity() == ModLoadingIssue.Severity.ERROR); }
    public List<ModLoadingIssue> getModLoadingIssues() { return issues; }
}
