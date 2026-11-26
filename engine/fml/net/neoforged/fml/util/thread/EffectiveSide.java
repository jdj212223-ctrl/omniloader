package net.neoforged.fml.util.thread;
import net.neoforged.fml.LogicalSide;
public class EffectiveSide {
    public static LogicalSide get() {
        ThreadGroup g = Thread.currentThread().getThreadGroup();
        return g instanceof SidedThreadGroup s ? s.getSide() : LogicalSide.CLIENT;
    }
}
