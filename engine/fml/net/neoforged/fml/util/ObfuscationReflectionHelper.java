package net.neoforged.fml.util;
import java.lang.reflect.*;
/** Minecraft already uses its real names here, so this is plain reflection. */
public class ObfuscationReflectionHelper {
    @SuppressWarnings("unchecked")
    public static <T, E> T getPrivateValue(Class<? super E> c, E instance, String name) {
        try { return (T) findField(c, name).get(instance); } catch (IllegalAccessException e) { throw new RuntimeException(e); }
    }
    public static <T, E> void setPrivateValue(Class<? super T> c, T instance, E value, String name) {
        try { findField(c, name).set(instance, value); } catch (IllegalAccessException e) { throw new RuntimeException(e); }
    }
    public static Method findMethod(Class<?> c, String name, Class<?>... params) {
        try { Method m = c.getDeclaredMethod(name, params); m.setAccessible(true); return m; } catch (NoSuchMethodException e) { throw new RuntimeException(e); }
    }
    public static <T> Constructor<T> findConstructor(Class<T> c, Class<?>... params) {
        try { Constructor<T> k = c.getDeclaredConstructor(params); k.setAccessible(true); return k; } catch (NoSuchMethodException e) { throw new RuntimeException(e); }
    }
    public static <T> Field findField(Class<? super T> c, String name) {
        try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException e) { throw new RuntimeException(e); }
    }
}
