package net.fabricmc.loader.api;

public interface LanguageAdapter {
    <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException;
}
