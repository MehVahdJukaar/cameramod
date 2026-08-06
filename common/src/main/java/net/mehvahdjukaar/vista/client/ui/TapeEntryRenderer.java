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
        blitCentered(graphics, texture, x, y, size, getAspectRatio(stack));
    }

    /**
     * Fits the picture inside the square cell the gallery gives it, keeping its own shape.
     */
    static void blitCentered(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size, float aspectRatio) {
        int width = size;
        int height = size;
        if (aspectRatio > 1) {
            height = Math.max(1, Math.round(size / aspectRatio));
        } else if (aspectRatio > 0 && aspectRatio < 1) {
            width = Math.max(1, Math.round(size * aspectRatio));
        }
        graphics.blit(texture, x + (size - width) / 2, y + (size - height) / 2, width, height, 0, 0, 1, 1, 1, 1);
    }

    static void renderUnknown(GuiGraphics graphics, ItemStack stack, int x, int y, int size) {
        graphics.fill(x, y, x + size, y + size, 0xFF888888);
        graphics.renderItem(stack, x + size / 2 - 8, y + size / 2 - 8);
    }
}
