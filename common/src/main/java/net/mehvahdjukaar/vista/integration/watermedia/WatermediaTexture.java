package net.mehvahdjukaar.vista.integration.watermedia;

import net.mehvahdjukaar.vista.client.textures.web.IWebTexture;
import net.mehvahdjukaar.vista.client.web.MediaStatus;
import net.mehvahdjukaar.vista.common.tv.TVBlockEntity;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.SOFTSourceSpatialize;
import org.watermedia.api.player.MediaPlayer;
import org.watermedia.api.util.MediaType;

public class WatermediaTexture extends AbstractTexture implements IWebTexture {

    private static final int UNSEEN_PAUSE_TICKS = 20;

    private final ResourceLocation textureLocation;
    private final WatermediaSession session;

    @Nullable
    private MediaPlayer player;
    private boolean playerFailed;
    private int framesDrawn;
    private int framesPaused;
    private int ticksUnseen;

    @Nullable
    private Vec3 speakerPos;
    private boolean speakerOn;

    public WatermediaTexture(ResourceLocation textureLocation, WatermediaSession session) {
        this.session = session;
        this.textureLocation = textureLocation;
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
        return player == null ? 0 : (int) player.texture();
    }

    @Override
    public void load(ResourceManager resourceManager) {
    }

    @Override
    public void releaseId() {
    }

    @Override
    public void close() {
        releasePlayer();
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    //render thread only
    @Nullable
    private MediaPlayer getOrCreatePlayer() {
        if (player != null || playerFailed || session.isClosed()) {
            return player;
        }
        if (session.isFetching()) {
            return null;
        }

        player = session.createPlayer();
        if (player == null) {
            playerFailed = true;
        }
        return player;
    }

    @Override
    public void updateAudio(TVBlockEntity tv, boolean playing) {
        if (playing) {
            speakerPos = tv.getSpeakerSourcePos();
        }
    }

    @Override
    public boolean isPlayingLoudAudio() {
        return speakerOn;
    }

    @Override
    public boolean isAudioOnly() {
        return player != null && (player.source().type() == MediaType.AUDIO);
    }

    @Override
    public MediaStatus uploadFrameAtTime(int ticks, float deltaTime, boolean paused) {
        framesDrawn++;
        if (paused) framesPaused++;

        MediaPlayer player = getOrCreatePlayer();
        if (player == null) {
            return (playerFailed || session.isFailed()) ? MediaStatus.FAILED : MediaStatus.LOADING;
        }
        if (player.texture() == MediaPlayer.NO_TEXTURE && !isAudioOnly()) {
            return MediaStatus.LOADING;
        }

        return switch (player.status()) {
            case WAITING, LOADING -> MediaStatus.LOADING;
            case BUFFERING -> MediaStatus.BUFFERING;
            case PLAYING, PAUSED -> MediaStatus.READY;
            case STOPPED, ENDED -> MediaStatus.CLOSED;
            case ERROR -> MediaStatus.FAILED;
        };
    }

    @Override
    public void tick() {
        if (session.isClosed()) releasePlayer();

        ticksUnseen = framesDrawn > 0 ? 0 : ticksUnseen + 1;
        if (player != null) {
            //tick without frames is norml at low fps, only stop once its been gone a while
            boolean unseen = ticksUnseen > UNSEEN_PAUSE_TICKS;
            if (framesDrawn > 0 || unseen) {
                boolean paused = unseen || framesPaused == framesDrawn || Minecraft.getInstance().isPaused();
                if (player.paused() != paused) {
                    player.pause(paused);
                }
            }

            updateSpeaker(player);
        }
        framesDrawn = 0;
        framesPaused = 0;
        speakerPos = null;
    }

    private void updateSpeaker(MediaPlayer mp) {
        int source = mp.audioSource();
        speakerOn = speakerPos != null && IWebTexture.distanceToCamera(speakerPos) <= IWebTexture.SPEAKER_RANGE;

        if (source == MediaPlayer.NO_SOURCE) return;

        if (!speakerOn) {
            mp.volume(0);
            return;
        }

        float blocks = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.BLOCKS);
        mp.volume((int) (blocks * ClientConfigs.AUDIO_VOLUME.get() * 100));

        //more nonsense state machine stuff
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        AL10.alSourcefv(source, AL10.AL_POSITION, new float[]{(float) speakerPos.x, (float) speakerPos.y, (float) speakerPos.z});
        AL10.alSourcei(source, AL10.AL_DISTANCE_MODEL, AL11.AL_LINEAR_DISTANCE);
        AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, IWebTexture.SPEAKER_RANGE);
        //set al to be sure
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1.0F);
        AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 0.0f);
        AL10.alSourcei(source, SOFTSourceSpatialize.AL_SOURCE_SPATIALIZE_SOFT, AL10.AL_TRUE);
    }
}
