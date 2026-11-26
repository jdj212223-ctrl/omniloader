package net.neoforged.fml.loading.progress;
public class Message {
    public enum MessageType { MC, ML, LOC, MOD }
    private final String text; private final MessageType type;
    public Message(String text, MessageType type) { this.text = text; this.type = type; }
    public String getText() { return text; }
    public float[] getTypeColour() { return new float[]{1f, 1f, 1f}; }
}
