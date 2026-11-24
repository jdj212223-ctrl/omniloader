package net.fabricmc.loader.api.metadata;

import net.fabricmc.loader.api.Version;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface ModMetadata {
    String getType();
    String getId();
    Collection<String> getProvides();
    Version getVersion();
    ModEnvironment getEnvironment();
    Collection<ModDependency> getDependencies();
    String getName();
    String getDescription();
    Collection<Person> getAuthors();
    Collection<Person> getContributors();
    ContactInformation getContact();
    Collection<String> getLicense();
    Optional<String> getIconPath(int size);
    boolean containsCustomValue(String key);
    CustomValue getCustomValue(String key);
    Map<String, CustomValue> getCustomValues();
}
