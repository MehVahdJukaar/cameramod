package net.mehvahdjukaar.vista.integration.exposure;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.mortuusars.exposure.ExposureClient;
import io.github.mortuusars.exposure.client.image.renderable.RenderableImage;
import io.github.mortuusars.exposure.client.render.image.ImageRenderer;
import io.github.mortuusars.exposure.client.render.image.RenderedImageInstance;
import io.github.mortuusars.exposure.client.render.photograph.PhotographStyle;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.item.AlbumItem;
import io.github.mortuusars.exposure.world.item.PhotographItem;
import io.github.mortuusars.exposure.world.item.StackedPhotographsItem;
import io.github.mortuusars.exposure.world.item.component.StackedPhotographs;
import io.github.mortuusars.exposure.world.item.component.album.AlbumPage;
import net.mehvahdjukaar.moonlight.api.misc.TField;
import net.mehvahdjukaar.moonlight.api.misc.TMethod;
import net.mehvahdjukaar.vista.client.ui.TapeEntryRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ExposurePictureRenderer implements TapeEntryRenderer {

    @Override
    public boolean matches(ItemStack stack) {
        return ExposureCompat.isExposurePicture(stack);
    }

    // the gallery goes through exposure's own photograph renderer, which handles paper frame and filters
    @Override
    public void render(GuiGraphics graphics, ItemStack stack, int x, int y, int size) {
        ItemStack toRender = stack;
        if (stack.getItem() instanceof AlbumItem album) {
            toRender = firstAlbumPhoto(album, stack);
        }

        if (!toRender.isEmpty()) {
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(x, y, 1.0);
            pose.scale(size, size, 1f);
            MultiBufferSource.BufferSource buffer = graphics.bufferSource();
            boolean rendered = ExposureClient.photographRenderer()
                    .render(toRender, false, false, pose, buffer, LightTexture.FULL_BRIGHT);
            graphics.flush();
            pose.popPose();
            if (rendered) return;
        }

        TapeEntryRenderer.renderUnknown(graphics, stack, x, y, size);
    }

    @Override
    @Nullable
    public ResourceLocation getTexture(ItemStack stack) {
        ItemStack photo = resolvePhoto(stack);
        if (!(photo.getItem() instanceof PhotographItem photographItem)) return null;
        Frame frame = photographItem.getFrame(photo);
        return getFrameTexture(photo, frame);
    }

    private static ItemStack resolvePhoto(ItemStack stack) {
        if (stack.getItem() instanceof PhotographItem) return stack;
        if (stack.getItem() instanceof StackedPhotographsItem stacked) {
            StackedPhotographs photos = stacked.getPhotographs(stack);
            return photos.isEmpty() ? ItemStack.EMPTY : photos.getItemUnsafe(0);
        }
        if (stack.getItem() instanceof AlbumItem album) {
            return firstAlbumPhoto(album, stack);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack firstAlbumPhoto(AlbumItem album, ItemStack stack) {
        for (AlbumPage page : album.getContent(stack).pages()) {
            ItemStack photo = page.photograph();
            if (photo.getItem() instanceof PhotographItem) return photo;
        }
        return ItemStack.EMPTY;
    }

    private static ResourceLocation getFrameTexture(ItemStack stack, Frame frame) {
        PhotographStyle style = PhotographStyle.of(stack);
        RenderableImage image = style.process(ExposureClient.renderedExposures().getOrCreate(frame));
        ImageRenderer imageRenderer = ExposureClient.imageRenderer();
        RenderedImageInstance texture = GET_OR_CREATE_INSTANCE.invoke(imageRenderer, image);
        ResourceLocation textureLocation = TEXTURE_LOCATION.get(texture);
        // upload the same way Exposure does when it renders the image itself
        if (REQUIRES_UPLOAD.get(texture)) {
            UPDATE_TEXTURE.invoke(texture);
            REQUIRES_UPLOAD.set(texture, false);
        }
        return textureLocation;
    }

    private static final TMethod<ImageRenderer, RenderedImageInstance> GET_OR_CREATE_INSTANCE =
            TMethod.of(ImageRenderer.class, "getOrCreateInstance", RenderableImage.class);
    private static final TField<RenderedImageInstance, Boolean> REQUIRES_UPLOAD =
            TField.of(RenderedImageInstance.class, "requiresUpload");
    private static final TField<RenderedImageInstance, ResourceLocation> TEXTURE_LOCATION =
            TField.of(RenderedImageInstance.class, "textureLocation");
    private static final TMethod<RenderedImageInstance, Void> UPDATE_TEXTURE =
            TMethod.of(RenderedImageInstance.class, "updateTexture");
}
