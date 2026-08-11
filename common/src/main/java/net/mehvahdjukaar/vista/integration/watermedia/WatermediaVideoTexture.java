package net.mehvahdjukaar.vista.integration.watermedia;

import net.mehvahdjukaar.vista.client.textures.IWebTexture;
import net.mehvahdjukaar.vista.client.web.MediaStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.watermedia.api.player.videolan.VideoPlayer;
import org.watermedia.videolan4j.factory.MediaPlayerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Executor;

public class WatermediaVideoTexture extends AbstractTexture implements IWebTexture {

    private final ResourceLocation textureLocation;
    private final WatermediaSession session;
    private final VideoPlayer videoPlayer;
    private volatile boolean released = false;

    public WatermediaVideoTexture(ResourceLocation textureLocation, WatermediaSession session, URI uri,
                                  int width, int height, Executor executor) {
        //TODO: figure out width and height
        this.videoPlayer = new VideoPlayer(new MediaPlayerFactory(), Minecraft.getInstance());
        this.session = session;
        this.textureLocation = textureLocation;
        this.videoPlayer.start(uri);
        this.videoPlayer.mute();
        this.videoPlayer.setRepeatMode(true);
        this.videoPlayer.setVolume(0);
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
        // After release, avoid returning a deleted texture name.
        if (released) {
            return 0;
        }
        return videoPlayer.texture();
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {

    }

    @Override
    public void close() {
        if (!released) {
            released = true;
            // Let Watermedia clean up its own GL texture.
            videoPlayer.release();
        }
    }

    @Override
    public void releaseId() {
        // no-op: the VideoPlayer / RenderAPI owns the GL texture lifetime, not Minecraft
    }

    @Override
    public MediaStatus uploadFrameAtTime(int ticks, float deltaTime, boolean paused) {
        videoPlayer.mute();
        if (videoPlayer.isPaused() != paused) {
            if (paused) videoPlayer.pause();
            else videoPlayer.resume();
        }

        if (videoPlayer.isBroken()) {
            return MediaStatus.FAILED;
        }
        if (videoPlayer.isEnded()) {
            return MediaStatus.CLOSED;
        }
        if (videoPlayer.isBuffering()) {
            return MediaStatus.BUFFERING;
        }

        if (videoPlayer.isReady()) return MediaStatus.READY;

        if (videoPlayer.isLoading()) {
            return MediaStatus.LOADING;
        }
        if (videoPlayer.isReady()) return MediaStatus.READY;

        if (videoPlayer.isWaiting()) {
            return MediaStatus.LOADING;
        }

        return MediaStatus.LOADING;

    }

}
