package net.fabricmc.fabric.api.itemgroup.v1;

import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.util.Collection;
import java.util.List;

/** Fabric API: the contents of a creative tab while it's being filled (backed by NeoForge's tab event). */
public class FabricItemGroupEntries implements CreativeModeTab.Output {
    private final BuildCreativeModeTabContentsEvent event;

    public FabricItemGroupEntries(BuildCreativeModeTabContentsEvent event) { this.event = event; }

    public CreativeModeTab.ItemDisplayParameters getContext() { return event.getParameters(); }
    public FeatureFlagSet getEnabledFeatures() { return event.getFlags(); }
    public boolean shouldShowOpRestrictedItems() { return event.hasPermissions(); }
    public List<ItemStack> getDisplayStacks() { return List.copyOf(event.getParentEntries()); }
    public List<ItemStack> getSearchTabStacks() { return List.copyOf(event.getSearchEntries()); }

    @Override
    public void accept(ItemStack stack, CreativeModeTab.TabVisibility visibility) {
        if (!contains(stack)) event.accept(stack, visibility);
    }

    private boolean contains(ItemStack stack) {
        for (ItemStack s : event.getParentEntries()) if (ItemStack.isSameItemSameComponents(s, stack)) return true;
        return false;
    }

    public void prepend(ItemStack stack, CreativeModeTab.TabVisibility v) { event.insertFirst(stack, v); }
    public void prepend(ItemStack stack) { prepend(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS); }
    public void prepend(ItemLike item) { prepend(new ItemStack(item)); }
    public void prepend(ItemLike item, CreativeModeTab.TabVisibility v) { prepend(new ItemStack(item), v); }

    public void addAfter(ItemLike after, ItemLike... items) { for (int i = items.length - 1; i >= 0; i--) insert(new ItemStack(after), new ItemStack(items[i]), true, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS); }
    public void addAfter(ItemStack after, ItemStack... stacks) { for (int i = stacks.length - 1; i >= 0; i--) insert(after, stacks[i], true, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS); }
    public void addAfter(ItemLike after, Collection<ItemStack> stacks) { addAfter(new ItemStack(after), stacks, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS); }
    public void addAfter(ItemStack after, Collection<ItemStack> stacks) { addAfter(after, stacks, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS); }
    public void addAfter(ItemLike after, Collection<ItemStack> stacks, CreativeModeTab.TabVisibility v) { addAfter(new ItemStack(after), stacks, v); }
    public void addAfter(ItemStack after, Collection<ItemStack> stacks, CreativeModeTab.TabVisibility v) {
        List<ItemStack> l = List.copyOf(stacks);
        for (int i = l.size() - 1; i >= 0; i--) insert(after, l.get(i), true, v);
    }
    public void addBefore(ItemLike before, ItemLike... items) { for (ItemLike i : items) insert(new ItemStack(before), new ItemStack(i), false, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS); }
    public void addBefore(ItemStack before, ItemStack... stacks) { for (ItemStack s : stacks) insert(before, s, false, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS); }
    public void addBefore(ItemLike before, Collection<ItemStack> stacks) { addBefore(new ItemStack(before), stacks, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS); }
    public void addBefore(ItemStack before, Collection<ItemStack> stacks) { addBefore(before, stacks, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS); }
    public void addBefore(ItemLike before, Collection<ItemStack> stacks, CreativeModeTab.TabVisibility v) { addBefore(new ItemStack(before), stacks, v); }
    public void addBefore(ItemStack before, Collection<ItemStack> stacks, CreativeModeTab.TabVisibility v) { for (ItemStack s : stacks) insert(before, s, false, v); }

    private void insert(ItemStack anchor, ItemStack stack, boolean after, CreativeModeTab.TabVisibility v) {
        if (contains(stack)) return;
        try {
            if (after) event.insertAfter(anchor, stack, v); else event.insertBefore(anchor, stack, v);
        } catch (RuntimeException e) {
            event.accept(stack, v); // the anchor isn't in this tab: just add it at the end
        }
    }
}
