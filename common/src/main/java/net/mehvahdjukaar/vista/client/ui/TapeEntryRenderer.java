package net.mehvahdjukaar.vista.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface TapeEntryRenderer {

    boolean matches(ItemStack stack);

    @Nullable
    ResourceLocation getTexture(ItemStack stack);

    /**
     * Width over height of the picture, used to fit it onto a TV screen of a different shape.
     */
    default float getAspectRatio(ItemStack stack) {
        return 1;
    }

    default void render(GuiGraphics graphics, ItemStack stack, int x, int y, int size) {
        ResourceLocation texture = getTexture(stack);
        if (texture == null) {
            renderUnknown(graphics, stack, x, y, size);
            return;
        }
        blitStretched(graphics, texture, x, y, size);
    }

    static void blitStretched(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size) {
        graphics.blit(texture, x, y, size, size, 0, 0, 1, 1, 1, 1);
    }

    static void renderUnknown(GuiGraphics graphics, ItemStack stack, int x, int y, int size) {
        graphics.fill(x, y, x + size, y + size, 0xFF888888);
        graphics.renderItem(stack, x + size / 2 - 8, y + size / 2 - 8);
    }
}
