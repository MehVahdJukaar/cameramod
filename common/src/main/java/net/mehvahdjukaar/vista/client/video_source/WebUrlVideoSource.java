package net.mehvahdjukaar.vista.client.video_source;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.mehvahdjukaar.moonlight.api.util.math.Vec2i;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.CrtOverlay;
import net.mehvahdjukaar.vista.client.textures.TvScreenVertexConsumers;
import net.mehvahdjukaar.vista.client.textures.web.IWebTexture;
import net.mehvahdjukaar.vista.client.textures.web.WebTexturesManager;
import net.mehvahdjukaar.vista.client.web.MediaError;
import net.mehvahdjukaar.vista.client.web.MediaStatus;
import net.mehvahdjukaar.vista.client.web.UrlUtils;
import net.mehvahdjukaar.vista.common.tv.IntAnimationState;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

public class WebUrlVideoSource implements IVideoSource {
    @Nullable
    private final URI uri;

    public WebUrlVideoSource(String url) {
        this.uri = UrlUtils.createUri(url);
    }

    @Override
    public @NotNull VertexConsumer getVideoFrameBuilder(TVBlockEntity tv, float partialTick, MultiBufferSource buffer,
                                                        boolean shouldUpdate, Vec2i screenSize, Vec2i pixelEffectRes,
                                                        int videoAnimationTick, boolean paused,
                                                        IntAnimationState switchAnim, IntAnimationState staticAnim,
                                                        boolean showsTime) {

        boolean noLinkSet = uri == null;
        if (noLinkSet) {
            return TvScreenVertexConsumers.getNoiseVC(buffer, pixelEffectRes, switchAnim);
        }

        IWebTexture texture = WebTexturesManager.getTexture(uri, tv.getBlockPos(), screenSize);
        MediaStatus state = texture.uploadFrameAtTime(videoAnimationTick, partialTick, paused);
        CrtOverlay overlay = CrtOverlay.NONE;
        if (state == MediaStatus.CLOSED) {
            overlay = CrtOverlay.DISCONNECT;
        }

        if (state == MediaStatus.FAILED) {
            ResourceLocation errorScreen = errorScreen(texture.getError());
            if (errorScreen != null) {
                return TvScreenVertexConsumers.getErrorVc(buffer, pixelEffectRes, errorScreen, switchAnim);
            }
            return TvScreenVertexConsumers.getNoiseVC(buffer, pixelEffectRes, switchAnim);
        } else if (state == MediaStatus.LOADING) {

            if (VistaModClient.isFFmpegDownloading()) {
               int ffmpegProg = VistaModClient.getFFmpegDownloadProgress();
                if (ffmpegProg >= 0) {
                    return TvScreenVertexConsumers.getDownloadingVc(buffer, pixelEffectRes, ffmpegProg, switchAnim);
                }
            }

            int progress = texture.getDownloadProgress();
            if (progress >= 0) {
                return TvScreenVertexConsumers.getDownloadingVc(buffer, pixelEffectRes, progress, switchAnim);
            }
            if (texture.isRetrying()) {
                return TvScreenVertexConsumers.getRetryingVc(buffer, pixelEffectRes, videoAnimationTick, switchAnim);
            }
            return TvScreenVertexConsumers.getWaitingVc(buffer, pixelEffectRes, videoAnimationTick, switchAnim);
        }
        if (state == MediaStatus.BUFFERING) {
            overlay = CrtOverlay.LOADING;
        }
        if (paused) {
            overlay = CrtOverlay.PAUSE;
        }
        if (texture.isAudioOnly()) {
            return TvScreenVertexConsumers.getAudioOnlyVc(buffer, pixelEffectRes, videoAnimationTick, overlay, switchAnim);
        }

        ResourceLocation textureId = texture.getTextureLocation();

        return TvScreenVertexConsumers.getSingleTextureVC(buffer, textureId, overlay, pixelEffectRes, switchAnim, staticAnim);

    }

    @Override
    public void updateAudio(TVBlockEntity tv, boolean playing) {
        if (uri == null) return;
        //only what the renderer already made, a tv that never drew must not start a download from here
        IWebTexture texture = WebTexturesManager.getTextureIfPresent(uri, tv.getBlockPos(), tv.getScreenPixelSize());
        boolean audible = playing && ClientConfigs.AUDIO_MODE.get().isOn(tv.hasSpeaker()) && !isSoundMuted();
        if (texture != null) texture.updateAudio(tv, audible);
    }

    private static boolean isSoundMuted() {
        Options options = Minecraft.getInstance().options;
        return options.getSoundSourceVolume(SoundSource.MASTER) <= 0 || options.getSoundSourceVolume(SoundSource.BLOCKS) <= 0;
    }

    @Override
    public boolean isPlayingMusic(TVBlockEntity tv) {
        if (uri == null) return false;
        IWebTexture texture = WebTexturesManager.getTextureIfPresent(uri, tv.getBlockPos(), tv.getScreenPixelSize());
        return texture != null && texture.isPlayingLoudAudio();
    }

    @Nullable
    private static ResourceLocation errorScreen(MediaError error) {
        return switch (error) {
            case FORBIDDEN -> VistaModClient.FORBIDDEN_SCREEN;
            case NOT_FOUND -> VistaModClient.NOT_FOUND_SCREEN;
            case BAD_LINK -> VistaModClient.BAD_LINK_SCREEN;
            case NO_FFMPEG, NONE -> null;
        };
    }
}
