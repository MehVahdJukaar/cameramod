package net.mehvahdjukaar.vista.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side registry of TapeEntryRenderers, used both by the tape gallery and by TV playback.
 * The vanilla ones are built in; integrations register their own (gated behind their mod-compat check).
 * Renderers are tried in registration order and the first match wins.
 */
public class PictureTapeRenderers {

    private static final List<TapeEntryRenderer> RENDERERS = new ArrayList<>();

    static {
        register(new MapTapeEntryRenderer());
        register(new PaintingTapeEntryRenderer());
    }

    public static void register(TapeEntryRenderer renderer) {
        RENDERERS.add(renderer);
    }

    public static void render(GuiGraphics graphics, ItemStack stack, int x, int y, int size) {
        TapeEntryRenderer renderer = find(stack);
        if (renderer == null) {
            TapeEntryRenderer.renderUnknown(graphics, stack, x, y, size);
            return;
        }
        renderer.render(graphics, stack, x, y, size);
    }

    @Nullable
    public static ResourceLocation getFrameTexture(ItemStack stack) {
        TapeEntryRenderer renderer = find(stack);
        return renderer == null ? null : renderer.getTexture(stack);
    }

    public static float getFrameAspectRatio(ItemStack stack) {
        TapeEntryRenderer renderer = find(stack);
        return renderer == null ? 1 : renderer.getAspectRatio(stack);
    }

    @Nullable
    private static TapeEntryRenderer find(ItemStack stack) {
        for (TapeEntryRenderer renderer : RENDERERS) {
            if (renderer.matches(stack)) return renderer;
        }
        return null;
    }
}
