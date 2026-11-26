package net.neoforged.fml.util.thread;
import net.neoforged.fml.LogicalSide;
public final class SidedThreadGroup extends ThreadGroup implements java.util.concurrent.ThreadFactory {
    private final LogicalSide side;
    SidedThreadGroup(LogicalSide side) { super(side.name()); this.side = side; }
    public LogicalSide getSide() { return side; }
    @Override public Thread newThread(Runnable r) { return new Thread(this, r); }
}
