package net.fabricmc.loader.api;

public class EntrypointException extends RuntimeException {
    private final String key;
    public EntrypointException(String key, Throwable cause) { super("Exception while loading entries for entrypoint '" + key + "'", cause); this.key = key; }
    public EntrypointException(String key, String causingMod, Throwable cause) { super("Exception while loading entries for entrypoint '" + key + "' provided by '" + causingMod + "'", cause); this.key = key; }
    public String getKey() { return key; }
}
