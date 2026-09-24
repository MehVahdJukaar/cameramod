package net.mehvahdjukaar.vista.client.textures.web;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.mehvahdjukaar.vista.client.VistaClientPlatStuff;
import net.mehvahdjukaar.vista.client.web.FFmpegMediaSession;
import net.mehvahdjukaar.vista.client.web.MediaFrame;
import net.mehvahdjukaar.vista.client.web.MediaStatus;
import net.mehvahdjukaar.vista.client.web.audio.TvSpeakerSoundInstance;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.integration.sable.SableCompatClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class FFmpegWebTexture extends DynamicTexture implements IWebTexture {
    private static final double NOT_STARTED = -1;
    private static final double AUDIO_RESYNC_THRESHOLD = 0.3;

    private final FFmpegMediaSession session;
    private final ResourceLocation textureLocation;
    @Nullable
    private MediaFrame lastOriginalFrame;
    private boolean wasFirstUploaded = false;
    private MediaStatus lastLookupState = MediaStatus.LOADING;
    @Nullable
    private TvSpeakerSoundInstance speakerSound;
    private double videoClockOffset = NOT_STARTED;
    private double audioClockOffset;

    public FFmpegWebTexture(ResourceLocation textureLocation, FFmpegMediaSession session, int width, int height) {
        super(width, height, false);
        this.session = session;
        this.textureLocation = textureLocation;
    }

    @Override
    public FFmpegMediaSession getSession() {
        return session;
    }

    @Override
    public ResourceLocation getTextureLocation() {
        return textureLocation;
    }

    @Override
    public void close() {
        //pixels are not closed here but by media frame
        this.releaseId();
        stopSpeaker();
    }

    @Override
    public void updateAudio(TVBlockEntity tv, boolean playing) {
        Vec3 center = SableCompatClient.projectOutOfSubLevel(tv, tv.getScreenRect().center());
        double tvClock = tv.getPlaybackTicks() / 20.0;
        syncVideoClock(tvClock);
        boolean canHear = playing && videoClockOffset != NOT_STARTED && lastLookupState != MediaStatus.BUFFERING;
        if (!canHear || IWebTexture.distanceToCamera(center) > SPEAKER_RANGE) {
            stopSpeaker();
            return;
        }
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        if (speakerSound != null && soundManager.isActive(speakerSound)) {
            speakerSound.moveTo(center);
            if (Math.abs(videoClockOffset - audioClockOffset) < AUDIO_RESYNC_THRESHOLD) return;
            stopSpeaker();
        }
        var audioSource = session.getAudioSource();
        if (audioSource.isEmpty()) return;
        speakerSound = VistaClientPlatStuff.createTvSpeakerSound(audioSource, center, getPlaybackSeconds(tvClock));
        audioClockOffset = videoClockOffset;
        soundManager.play(speakerSound);
    }

    private void stopSpeaker() {
        if (speakerSound == null) return;
        Minecraft.getInstance().getSoundManager().stop(speakerSound);
        speakerSound = null;
    }

    @Override
    public boolean isPlayingLoudAudio() {
        return speakerSound != null && speakerSound.isLoud();
    }

    @Override
    public MediaStatus uploadFrameAtTime(int ticks, float deltaTime, boolean paused) {
        double tvClock = (ticks + deltaTime) / 20.0;
        syncVideoClock(tvClock);
        double seconds = getPlaybackSeconds(tvClock);

        var lookup = session.lookupFrame(seconds);
        this.lastLookupState = lookup.state();
        if (lookup.state() == MediaStatus.BUFFERING) {
            videoClockOffset = tvClock - session.getBufferedSeconds();
        }
        MediaFrame frame = lookup.frame();
        if (frame != null && frame != this.lastOriginalFrame) {
            uploadOnRenderThread(frame.image());
            this.lastOriginalFrame = frame;
        }
        if (!wasFirstUploaded && !session.isAudioOnly() && lookup.state().isGood()) {
            return MediaStatus.LOADING;
        }
        return lookup.state();
    }

    private void syncVideoClock(double tvClock) {
        if (!session.isReady()) {
            videoClockOffset = NOT_STARTED;
        } else if (videoClockOffset == NOT_STARTED || tvClock < videoClockOffset) {
            videoClockOffset = tvClock;
        }
    }

    private double getPlaybackSeconds(double tvClock) {
        if (videoClockOffset == NOT_STARTED) return 0;
        return tvClock - videoClockOffset;
    }

    private void uploadOnRenderThread(NativeImage newPixels) {
        Runnable upload = () -> {
            NativeImage oldPixels = this.pixels;
            this.pixels = newPixels;
            if (oldPixels == null) {
                TextureUtil.prepareImage(this.getId(), newPixels.getWidth(), newPixels.getHeight());
            }
            this.upload();
            wasFirstUploaded = true;
        };
        if (RenderSystem.isOnRenderThread()) {
            upload.run();
        } else {
            RenderSystem.recordRenderCall(upload::run);
        }
    }

}
