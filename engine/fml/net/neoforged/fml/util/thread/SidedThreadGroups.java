package net.neoforged.fml.util.thread;
import net.neoforged.fml.LogicalSide;
public final class SidedThreadGroups {
    public static final SidedThreadGroup CLIENT = new SidedThreadGroup(LogicalSide.CLIENT);
    public static final SidedThreadGroup SERVER = new SidedThreadGroup(LogicalSide.SERVER);
}
