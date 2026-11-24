package net.fabricmc.loader.api.metadata;

import java.util.Map;
import java.util.Optional;

public interface ContactInformation {
    ContactInformation EMPTY = new ContactInformation() {
        public Optional<String> get(String key) { return Optional.empty(); }
        public Map<String, String> asMap() { return Map.of(); }
    };
    Optional<String> get(String key);
    Map<String, String> asMap();
}
