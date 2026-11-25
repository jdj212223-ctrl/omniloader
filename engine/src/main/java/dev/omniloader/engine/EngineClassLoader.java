package dev.omniloader.engine;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.function.BiFunction;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Omniloader's class loader: loads Minecraft (already translated to real names) and mods itself,
 * changing each class as it loads (mods' access changes and mixins). Everything else (Java, LWJGL, Netty...)
 * comes from the normal class path.
 */
public final class EngineClassLoader extends ClassLoader {
    static { registerAsParallelCapable(); }

    private record Source(ZipFile zip, URL url, ProtectionDomain domain) {}
    private final List<Source> sources = new ArrayList<>();
    private volatile BiFunction<String, byte[], byte[]> transformer = (n, b) -> b;

    public EngineClassLoader(ClassLoader parent) { super("omniloader", parent); }

    public void addJar(Path jar) throws IOException {
        URL url = jar.toUri().toURL();
        sources.add(new Source(new ZipFile(jar.toFile()), url, new ProtectionDomain(new CodeSource(url, (Certificate[]) null), null, this, null)));
    }

    public void setTransformer(BiFunction<String, byte[], byte[]> t) { this.transformer = t; }

    private Source find(String path) {
        for (Source s : sources) if (s.zip.getEntry(path) != null) return s;
        return null;
    }

    /** The class file as it is on disk (before mixins), from Minecraft, a mod, or the normal class path. */
    public byte[] rawBytes(String binaryName) throws IOException {
        String path = binaryName.replace('.', '/') + ".class";
        Source s = find(path);
        if (s != null) try (InputStream in = s.zip.getInputStream(s.zip.getEntry(path))) { return in.readAllBytes(); }
        try (InputStream in = getParent().getResourceAsStream(path)) { return in == null ? null : in.readAllBytes(); }
    }

    public boolean owns(String binaryName) { return find(binaryName.replace('.', '/') + ".class") != null; }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                if (!name.startsWith("java.") && owns(name)) c = findClass(name);   // Minecraft and mods: ours, changed on load
                else c = getParent().loadClass(name);
            }
            if (resolve) resolveClass(c);
            return c;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        Source s = find(path);
        if (s == null) throw new ClassNotFoundException(name);
        try {
            byte[] b;
            try (InputStream in = s.zip.getInputStream(s.zip.getEntry(path))) { b = in.readAllBytes(); }
            b = transformer.apply(name, b);
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                String pkg = name.substring(0, dot);
                if (getDefinedPackage(pkg) == null) try { definePackage(pkg, null, null, null, null, null, null, null); } catch (IllegalArgumentException ignored) {}
            }
            return defineClass(name, b, 0, b.length, s.domain);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }

    @Override
    public URL getResource(String name) {
        Source s = find(name);
        if (s != null) {
            try { return new URL("jar:" + s.url + "!/" + name); } catch (MalformedURLException e) { return null; }
        }
        return getParent().getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        List<URL> out = new ArrayList<>();
        for (Source s : sources) if (s.zip.getEntry(name) != null) out.add(new URL("jar:" + s.url + "!/" + name));
        out.addAll(Collections.list(getParent().getResources(name)));
        return Collections.enumeration(out);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        Source s = find(name);
        if (s != null) try { return s.zip.getInputStream(s.zip.getEntry(name)); } catch (IOException e) { return null; }
        return getParent().getResourceAsStream(name);
    }
}
