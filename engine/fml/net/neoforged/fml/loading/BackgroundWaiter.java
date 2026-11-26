package net.neoforged.fml.loading;
public class BackgroundWaiter {
    public static void runAndTick(Runnable r, Runnable tick) { r.run(); }
}
