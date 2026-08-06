package net.mehvahdjukaar.vista.integration.camera_mod;

import com.mojang.blaze3d.platform.NativeImage;
import de.maxhenkel.camera.ImageData;
import de.maxhenkel.camera.TextureCache;
import net.mehvahdjukaar.vista.client.ui.TapeEntryRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class CameraModPictureRenderer implements TapeEntryRenderer {

    @Override
    public boolean matches(ItemStack stack) {
        return CameraModCompat.isPicture(stack);
    }

    // Camera images are files kept on the server, so the first call only asks for the image and comes
    // back empty. Callers fall back to static/the item icon until it has finished downloading.
    @Override
    @Nullable
    public ResourceLocation getTexture(ItemStack stack) {
        ImageData data = ImageData.fromStack(stack);
        if (data == null) return null;
        return TextureCache.instance().getImage(data.getId());
    }

    @Override
    public float getAspectRatio(ItemStack stack) {
        ImageData data = ImageData.fromStack(stack);
        if (data == null) return 1;
        NativeImage image = TextureCache.instance().getNativeImage(data.getId());
        return image == null ? 1 : (float) image.getWidth() / image.getHeight();
    }
}
