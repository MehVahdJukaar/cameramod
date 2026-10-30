package net.mehvahdjukaar.vista.integration.joy_of_painting;

import net.mehvahdjukaar.vista.client.ui.picture_tape.TapeEntryRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class CanvasTapeEntryRenderer implements TapeEntryRenderer {

    @Override
    public boolean matches(ItemStack stack) {
        return JoyOfPaintingCompat.isCanvas(stack);
    }

    @Override
    @Nullable
    public ResourceLocation getTexture(ItemStack stack) {
        return CanvasTapeTextures.getOrCreate(stack);
    }
}
