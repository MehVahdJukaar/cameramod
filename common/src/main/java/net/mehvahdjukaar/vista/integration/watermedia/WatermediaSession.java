package net.mehvahdjukaar.vista.integration.watermedia;

import net.mehvahdjukaar.vista.client.textures.web.IWebTexture;
import net.mehvahdjukaar.vista.client.web.IMediaSession;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.watermedia.api.image.ImageAPI;
import org.watermedia.api.image.ImageCache;
import org.watermedia.api.player.videolan.VideoPlayer;
import org.watermedia.shaded.kiulian.downloader.downloader.client.DefaultClients;
import org.watermedia.videolan4j.factory.MediaPlayerFactory;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public class WatermediaSession implements IMediaSession {

    private static final Set<WatermediaSession> OPEN = ConcurrentHashMap.newKeySet();

    private final Executor executor;

    private final ImageCache imageCache;
    private final int targetWidth;
    private final int targetHeight;

    //one vlc player for every screen showing this url
    @Nullable
    private VideoPlayer videoPlayer;
    private final VlcAudioRingBuffer audio = new VlcAudioRingBuffer();
    private int framesDrawn;
    private int framesPaused;

    public WatermediaSession(URI uri, Executor executor,
                             int targetWidth, int targetHeight) {
        //TODO: check executor
        this.imageCache = ImageAPI.getCache(uri, executor);
        this.executor = executor;
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;
        this.imageCache.load();
        OPEN.add(this);
    }

    public static void initHack() {
        try {
            var c = Class.forName("org.watermedia.api.network.patchs.YoutubePatch");
            Field field = c.getDeclaredField("WORKING_CLIENT");
            field.setAccessible(true);
            field.set(null, DefaultClients.VALUES[0]);
        } catch (Throwable ignored) {
        }

    }

    public static void onClientTick() {
        for (WatermediaSession session : OPEN) session.applyRequests();
    }

    @Override
    public IWebTexture createTextureView(ResourceLocation resourceLocation) {
        return isVideo() ?
                new WatermediaVideoTexture(resourceLocation, this, getOrCreatePlayer()) :
                new WatermediaImageTexture(resourceLocation, this, imageCache, targetWidth, targetHeight, executor);
    }

    private VideoPlayer getOrCreatePlayer() {
        if (videoPlayer == null) {
            //TODO: figure out width and height
            videoPlayer = new VideoPlayer(new MediaPlayerFactory(), Minecraft.getInstance());
            audio.attach(videoPlayer);
            videoPlayer.start(imageCache.uri);
            videoPlayer.setRepeatMode(true);
            videoPlayer.setVolume(100);
        }
        return videoPlayer;
    }

    public void requestPlayback(boolean paused) {
        framesDrawn++;
        if (paused) framesPaused++;
    }

    public VlcAudioRingBuffer getAudio() {
        return audio;
    }

    private void applyRequests() {
        if (videoPlayer != null && framesDrawn > 0) {
            boolean paused = framesPaused == framesDrawn;
            if (videoPlayer.isPaused() != paused) {
                if (paused) videoPlayer.pause();
                else videoPlayer.resume();
            }
        }
        framesDrawn = 0;
        framesPaused = 0;
    }

    @Override
    public boolean shouldRefreshTexture(IWebTexture tt) {
        if (isVideo()) {
            //this was a video after all. we make a texture then
            return tt instanceof WatermediaImageTexture;
        }
        return false;
    }

    private boolean isVideo() {
        ImageCache.Status status = imageCache.getStatus();
        return (status == ImageCache.Status.READY && imageCache.isVideo());
    }

    @Override
    public boolean isFailed() {
        return imageCache.getException() != null;
    }

    public boolean isClosed() {
        return !OPEN.contains(this);
    }

    @Override
    public void close() {
        OPEN.remove(this);
        if (videoPlayer != null) {
            videoPlayer.release();
            videoPlayer = null;
        }
        imageCache.deuse();
    }
}
