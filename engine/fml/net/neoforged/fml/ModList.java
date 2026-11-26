package net.neoforged.fml;
import net.neoforged.fml.loading.moddiscovery.*;
import net.neoforged.neoforgespi.language.*;
import net.neoforged.neoforgespi.locating.IModFile;
import java.util.*; import java.util.function.*; import java.util.stream.*;
public class ModList {
    private static ModList INSTANCE;
    private final List<IModFileInfo> files;
    private final List<IModInfo> mods;
    private final Map<String, ModFileInfo> fileById = new HashMap<>();
    private List<ModContainer> containers = List.of();
    private Map<String, ModContainer> byId = Map.of();

    private ModList(List<ModFile> files, List<ModInfo> sortedMods) {
        this.files = files.stream().map(ModFile::getModFileInfo).filter(Objects::nonNull).<IModFileInfo>map(i -> i).toList();
        this.mods = List.copyOf(sortedMods);
        for (ModInfo m : sortedMods) fileById.put(m.getModId(), m.getOwningFile());
    }
    public static ModList of(List<ModFile> files, List<ModInfo> sortedMods) { INSTANCE = new ModList(files, sortedMods); return INSTANCE; }
    public static ModList get() { return INSTANCE; }
    public void setLoadedMods(List<ModContainer> c) {
        containers = List.copyOf(c);
        Map<String, ModContainer> m = new LinkedHashMap<>();
        for (ModContainer x : c) m.put(x.getModId(), x);
        byId = m;
    }
    public List<IModFileInfo> getModFiles() { return files; }
    public IModFileInfo getModFileById(String id) { return fileById.get(id); }
    public Optional<? extends ModContainer> getModContainerById(String id) { return Optional.ofNullable(byId.get(id)); }
    public Optional<? extends ModContainer> getModContainerByObject(Object o) { return containers.stream().filter(c -> c instanceof net.neoforged.fml.javafmlmod.FMLModContainer f && f.instances().contains(o)).findFirst(); }
    public List<IModInfo> getMods() { return mods; }
    public boolean isLoaded(String id) { return fileById.containsKey(id); }
    public int size() { return mods.size(); }
    public List<ModFileScanData> getAllScanData() { return files.stream().map(f -> f.getFile().getScanResult()).toList(); }
    public void forEachModFile(Consumer<IModFile> c) { files.stream().map(IModFileInfo::getFile).forEach(c); }
    public <T> Stream<T> applyForEachModFile(Function<IModFile, T> fn) { return files.stream().map(IModFileInfo::getFile).map(fn); }
    public <T> Stream<T> applyForEachModFileAlphabetical(Function<IModFile, T> fn) { return files.stream().map(IModFileInfo::getFile).sorted(Comparator.comparing(IModFile::getFileName)).map(fn); }
    public void forEachModContainer(BiConsumer<String, ModContainer> c) { byId.forEach(c); }
    public List<ModContainer> getSortedMods() { return containers; }
    public void forEachModInOrder(Consumer<ModContainer> c) { containers.forEach(c); }
    public <T> Stream<T> applyForEachModContainer(Function<ModContainer, T> fn) { return containers.stream().map(fn); }
}
