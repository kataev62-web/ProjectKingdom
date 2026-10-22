package net.minecraft.world.inventory;

/** Forge 1.20.1-compatible harness stand-in for the vanilla transient crafting grid. */
public final class TransientCraftingContainer extends CraftingContainer {
    public TransientCraftingContainer(AbstractContainerMenu menu, int width, int height) {
        super(menu, width, height);
    }
}
