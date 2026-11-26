package net.neoforged.fml.loading.progress;
public final class ProgressMeter {
    private final String name; private int steps, current; private Message label;
    public ProgressMeter(String name, int steps, int current, Message label) { this.name = name; this.steps = steps; this.current = current; this.label = label; }
    public String name() { return name; }
    public int steps() { return steps; }
    public int current() { return current; }
    public Message label() { return label; }
    public void increment() { current++; }
    public void complete() { current = steps; StartupNotificationManager.popBar(this); }
    public float progress() { return steps == 0 ? 1f : current / (float) steps; }
    public void setAbsolute(int v) { current = v; }
    public void label(String s) { label = new Message(s, Message.MessageType.MOD); }
}
