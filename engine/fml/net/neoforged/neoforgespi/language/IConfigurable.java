package net.neoforged.neoforgespi.language;
import java.util.*;
public interface IConfigurable {
    <T> Optional<T> getConfigElement(String... key);
    List<? extends IConfigurable> getConfigList(String... key);
}
