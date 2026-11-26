package net.neoforged.fml.loading.moddiscovery;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.neoforged.neoforgespi.language.IConfigurable;
import java.util.*;
/** A mods.toml section, readable by key path. */
public final class NightConfigWrapper implements IConfigurable {
    private final UnmodifiableConfig config;
    public NightConfigWrapper(UnmodifiableConfig config) { this.config = config; }
    public UnmodifiableConfig raw() { return config; }
    @Override @SuppressWarnings("unchecked") public <T> Optional<T> getConfigElement(String... key) {
        Object v = config.getRaw(Arrays.asList(key));
        if (v instanceof UnmodifiableConfig c) return Optional.of((T) c.valueMap());
        return Optional.ofNullable((T) v);
    }
    @Override public List<? extends IConfigurable> getConfigList(String... key) {
        Object v = config.getRaw(Arrays.asList(key));
        if (!(v instanceof List<?> l)) return List.of();
        List<IConfigurable> out = new ArrayList<>();
        for (Object o : l) if (o instanceof UnmodifiableConfig c) out.add(new NightConfigWrapper(c));
        return out;
    }
}
