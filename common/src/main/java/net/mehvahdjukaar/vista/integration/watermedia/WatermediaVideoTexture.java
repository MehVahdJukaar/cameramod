package net.mehvahdjukaar.vista.integration.watermedia;

import net.mehvahdjukaar.vista.client.VistaClientPlatStuff;
import net.mehvahdjukaar.vista.client.textures.web.IWebTexture;
import net.mehvahdjukaar.vista.client.web.MediaStatus;
import net.mehvahdjukaar.vista.client.web.audio.TvSpeakerSoundInstance;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.watermedia.api.player.videolan.VideoPlayer;

import java.io.IOException;

//a view on the session's player. many screens can share one
public class WatermediaVideoTexture extends AbstractTexture implements IWebTexture {

    //vlc sets up the video surface right as it starts, give it some frames before calling it audio only
    private static final int NO_VIDEO_GRACE = 20;

    private final ResourceLocation textureLocation;
    private final WatermediaSession session;
    private final VideoPlayer videoPlayer;
    private int readyFrames;
    @Nullable
    private TvSpeakerSoundInstance speakerSound;

    public WatermediaVideoTexture(ResourceLocation textureLocation, WatermediaSession session, VideoPlayer videoPlayer) {
        this.session = session;
        this.textureLocation = textureLocation;
        this.videoPlayer = videoPlayer;
    }

    @Override
    public WatermediaSession getSession() {
        return session;
    }

    @Override
    public ResourceLocation getTextureLocation() {
        return textureLocation;
    }

    @Override
    public int getId() {
        //player is gone with the session, dont hand out a deleted texture name
        if (session.isClosed()) return 0;
        return videoPlayer.texture();
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {
    }

    @Override
    public void close() {
        stopSpeaker();
    }

    @Override
    public void releaseId() {
    }

    @Override
    public void updateAudio(TVBlockEntity tv, boolean playing) {
        Vec3 center = tv.getScreenRect().center();
        if (!playing || IWebTexture.distanceToCamera(center) > SPEAKER_RANGE) {
            stopSpeaker();
            return;
        }
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        if (speakerSound != null && soundManager.isActive(speakerSound)) return;
        if (session.getAudio().isEmpty()) return;
        speakerSound = VistaClientPlatStuff.createTvSpeakerSound(session.getAudio(), center, 0);
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
    public boolean isAudioOnly() {
        return readyFrames > NO_VIDEO_GRACE && videoPlayer.width() <= 1;
    }

    @Override
    public MediaStatus uploadFrameAtTime(int ticks, float deltaTime, boolean paused) {
        session.requestPlayback(paused);

        if (videoPlayer.isBroken()) return MediaStatus.FAILED;
        if (videoPlayer.isEnded()) return MediaStatus.CLOSED;
        if (videoPlayer.isBuffering()) return MediaStatus.BUFFERING;
        if (videoPlayer.isReady()) {
            readyFrames++;
            return MediaStatus.READY;
        }
        return MediaStatus.LOADING;
    }

}
