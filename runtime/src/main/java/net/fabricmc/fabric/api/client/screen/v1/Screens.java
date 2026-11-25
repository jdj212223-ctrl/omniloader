package net.fabricmc.fabric.api.client.screen.v1;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

import java.util.AbstractList;
import java.util.List;

/** Fabric API: helpers for screens. getButtons() is a live list: adding to it adds the widget to the screen. */
public final class Screens {
    private Screens() {}

    public static List<AbstractWidget> getButtons(Screen screen) {
        return new AbstractList<>() {
            private List<AbstractWidget> view() {
                return screen.renderables.stream().filter(r -> r instanceof AbstractWidget).map(r -> (AbstractWidget) r).toList();
            }
            @Override public AbstractWidget get(int i) { return view().get(i); }
            @Override public int size() { return view().size(); }
            @Override public void add(int i, AbstractWidget w) {
                screen.renderables.add((Renderable) w);
                screen.children.add((GuiEventListener) w);
                screen.narratables.add((NarratableEntry) w);
            }
            @Override public AbstractWidget remove(int i) {
                AbstractWidget w = view().get(i);
                screen.renderables.remove(w); screen.children.remove(w); screen.narratables.remove(w);
                return w;
            }
            @Override public AbstractWidget set(int i, AbstractWidget w) { AbstractWidget old = remove(i); add(i, w); return old; }
        };
    }

    public static Font getTextRenderer(Screen screen) { return screen.font; }
    public static Minecraft getClient(Screen screen) { return screen.minecraft; }
}
