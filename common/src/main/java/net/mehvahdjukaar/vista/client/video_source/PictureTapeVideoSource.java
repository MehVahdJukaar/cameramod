package net.mehvahdjukaar.vista.client.video_source;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.client.CrtOverlay;
import net.mehvahdjukaar.vista.client.textures.ScreenFit;
import net.mehvahdjukaar.vista.client.textures.TvScreenVertexConsumers;
import net.mehvahdjukaar.vista.client.ui.PictureTapeRenderers;
import net.mehvahdjukaar.vista.common.picture_tape.PictureTapeContent;
import net.mehvahdjukaar.vista.common.picture_tape.PictureTapeItem;
import net.mehvahdjukaar.vista.common.tv.IntAnimationState;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Plays a picture tape on a TV as a slideshow, showing each picture for its stored play speed.
 */
public class PictureTapeVideoSource implements IVideoSource {

    private final List<ItemStack> pictures;
    private final int playSpeed;
    // picture the last built frame was for, so the screen fit can be asked for right after
    private ItemStack shownPicture = ItemStack.EMPTY;

    public PictureTapeVideoSource(ItemStack tape) {
        PictureTapeContent content = PictureTapeItem.getContent(tape);
        this.pictures = content.pictures().toList();
        this.playSpeed = Math.max(1, content.playbackSpeed());
    }

    @Override
    public int getVideoDuration() {
        return Math.max(1, pictures.size()) * playSpeed;
    }

    @Override
    public @NotNull VertexConsumer getVideoFrameBuilder(
            float partialTick, MultiBufferSource buffer, boolean shouldUpdate, Vec2i screenSize, Vec2i pixelEffectRes,
            int videoAnimationTick, boolean paused,
            IntAnimationState switchAnim, IntAnimationState staticAnim, boolean showsTime) {

        if (pictures.isEmpty()) {
            this.shownPicture = ItemStack.EMPTY;
            return TvScreenVertexConsumers.getBarsVC(buffer, pixelEffectRes, switchAnim);
        }
        int index = (videoAnimationTick / playSpeed) % pictures.size();
        this.shownPicture = pictures.get(index);
        ResourceLocation texture = PictureTapeRenderers.getFrameTexture(shownPicture);
        if (texture == null) {
            return TvScreenVertexConsumers.getNoiseVC(buffer, pixelEffectRes, switchAnim);
        }
        CrtOverlay overlay = paused ? CrtOverlay.PAUSE : CrtOverlay.NONE;
        return TvScreenVertexConsumers.getSingleTextureVC(buffer, texture, overlay, pixelEffectRes, switchAnim, staticAnim);
    }

    @Override
    public ScreenFit getScreenFit() {
        if (shownPicture.isEmpty()) return ScreenFit.FILL;
        return new ScreenFit(ClientConfigs.PICTURE_TAPE_SCALING_MODE.get(),
                PictureTapeRenderers.getFrameAspectRatio(shownPicture));
    }
}
