package net.mehvahdjukaar.vista.client.web;

import com.mojang.blaze3d.platform.NativeImage;
import net.mehvahdjukaar.moonlight.api.util.FileDownloadUtils;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.VistaModClient;
import net.mehvahdjukaar.vista.client.textures.ImageRescaler;
import net.mehvahdjukaar.vista.client.textures.web.FFmpegWebTexture;
import net.mehvahdjukaar.vista.client.textures.web.IWebTexture;
import net.mehvahdjukaar.vista.client.web.audio.FFmpegAudioTrack;
import net.mehvahdjukaar.vista.client.web.ffmpeg.FFmpeg;
import net.mehvahdjukaar.vista.configs.ClientConfigs;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class FFmpegMediaSession implements IMediaSession {
    private final List<NativeImage> frames = new ArrayList<>(); //all frames are 1/framerate apart
    private final FFmpegAudioTrack audio = new FFmpegAudioTrack();

    @Nullable
    private FFmpegMediaDecoder decoder;
    private volatile double framerate;
    private volatile boolean completed;
    @Nullable
    private volatile MediaError error;
    private volatile boolean retrying;
    private volatile boolean isClosed;
    private volatile boolean audioOnly;
    private volatile int downloadProgress = -1;
    private boolean holdsCachedFile;

    private final URI uri;
    private final MediaCacheManager cacheManager;
    private final int targetWidth;
    private final int targetHeight;

    public FFmpegMediaSession(URI uri, MediaCacheManager cacheManager, Executor executor, int targetWidth, int targetHeight) {
        this.uri = uri;
        this.cacheManager = cacheManager;
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;
        executor.execute(() -> downloadAndDecode(executor));
    }

    private void downloadAndDecode(Executor executor) {
        Path streamCopy = null;
        boolean fullCopy = false;
        try {
            String input;
            if (isHttp(uri) && !cacheManager.isCached(uri)) {
                checkReachable(uri);
                input = uri.toString();
                streamCopy = cacheManager.newPartialFile(uri);
            } else {
                input = downloadToCache();
                if (input == null) {
                    return;
                }
            }

            FFmpeg ffmpeg = VistaModClient.awaitFFmpeg();
            if (ffmpeg == null) {
                setFailed(MediaError.NO_FFMPEG);
                return;
            }
            FFmpegMediaDecoder newDecoder = openDecoder(ffmpeg, input, streamCopy);
            if (newDecoder == null) {
                return;
            }

            FFmpegMediaDecoder.StreamInfo info;
            try {
                info = newDecoder.probeStreams();
            } catch (IOException e) {
                if (streamCopy == null) throw e;
                //added because on linux it segfaults for some reason
                VistaMod.LOGGER.warn("Could not stream {}, downloading it instead", uri, e);
                cacheManager.finishPartialFile(uri, streamCopy, false);
                streamCopy = null;

                input = downloadToCache();
                if (input == null) return;
                newDecoder = openDecoder(ffmpeg, input, null);
                if (newDecoder == null) {
                    return;
                }
                info = newDecoder.probeStreams();
            }
            if (!info.hasVideo() && !info.hasAudio()) {
                VistaMod.LOGGER.warn("No video or audio stream in {}, not playable media", input);
                setFailed(MediaError.BAD_LINK);
                return;
            }
            if (info.hasVideo()) {
                VistaMod.LOGGER.info("Video: {}x{}, {} fps", info.width(), info.height(), info.framerate());
            } else {
                VistaMod.LOGGER.info("No video stream in {}, playing audio only", input);
            }
            this.framerate = info.framerate();
            this.audioOnly = !info.hasVideo();

            boolean exitedCleanly = newDecoder.decode(info, audio, this::addFrame, executor);
            if (isClosed) return;

            boolean gotNothing = audioOnly ? audio.isEmpty() : frameCount() == 0;
            if (gotNothing) {
                setFailed(MediaError.NONE);
                return;
            }
            VistaMod.LOGGER.info("Finished decoding {}, total frames: {}", input, frameCount());
            this.completed = true;
            fullCopy = exitedCleanly;
        } catch (Exception e) {
            if (isClosed) return;

            setFailed(MediaError.classify(e));
            VistaMod.LOGGER.error("Failed to load web video {}", uri, e);
        } finally {
            this.downloadProgress = -1;
            this.retrying = false;
            if (streamCopy != null) {
                cacheManager.finishPartialFile(uri, streamCopy, fullCopy && !isClosed);
            }
        }
    }

    @Nullable
    private String downloadToCache() throws Exception {
        Path videoPath = cacheManager.getOrDownload(uri,
                percent -> this.downloadProgress = percent,
                (attempt, max, cause) -> this.retrying = true);
        this.retrying = false;
        this.downloadProgress = -1;

        synchronized (this) {
            if (isClosed) {
                cacheManager.release(uri);
                return null;
            }
            holdsCachedFile = true;
        }
        return videoPath.toString();
    }

    @Nullable
    private synchronized FFmpegMediaDecoder openDecoder(FFmpeg ffmpeg, String input, @Nullable Path copyTo) {
        if (isClosed) return null;
        this.decoder = new FFmpegMediaDecoder(ffmpeg, input, copyTo);
        return this.decoder;
    }

    private static boolean isHttp(URI uri) {
        String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    //ffmpeg only says "failed", this keeps the forbidden/not found screens
    private static void checkReachable(URI uri) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        try {
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Range", "bytes=0-0");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            int code = conn.getResponseCode();
            if (code >= 400) throw new FileDownloadUtils.HttpStatusException(code, uri.toString());
        } finally {
            conn.disconnect();
        }
    }

    private void addFrame(NativeImage frame) {
        NativeImage scaled = ImageRescaler.resize(frame, targetWidth, targetHeight,
                ClientConfigs.SCALING_MODE.get(), ClientConfigs.BILINEAR.get());

        if (frame != scaled) {
            frame.close();
        }
        synchronized (this) {
            if (isClosed) {
                scaled.close();
                return;
            }
            frames.add(scaled);
        }
    }

    private void setFailed(MediaError error) {
        this.error = error;
    }

    private synchronized int frameCount() {
        return frames.size();
    }

    @Override
    public boolean isAudioOnly() {
        return audioOnly;
    }

    public boolean isReady() {
        return audioOnly || frameCount() > 0;
    }

    public FFmpegAudioTrack getAudioTrack() {
        return audio;
    }

    public synchronized double getLoopSeconds() {
        if (audioOnly) {
            return audio.getDurationSeconds();
        }
        return completed ? frames.size() / framerate : 0;
    }

    public synchronized double getBufferedSeconds() {
        return frames.size() / framerate;
    }

    @Override
    public boolean isFailed() {
        return error != null;
    }

    @Override
    public MediaError getError() {
        MediaError e = error;
        return e == null ? MediaError.NONE : e;
    }

    @Override
    public boolean isRetrying() {
        return retrying;
    }

    @Override
    public int getDownloadProgress() {
        return downloadProgress;
    }

    public synchronized MediaStatus.Pair lookupFrame(double seconds) {
        boolean failed = isFailed();
        MediaStatus state = failed ? MediaStatus.FAILED : isClosed ? MediaStatus.CLOSED : MediaStatus.READY;
        if (audioOnly) {
            return new MediaStatus.Pair(state, null);
        }
        if (frames.isEmpty()) {
            return new MediaStatus.Pair(failed ? MediaStatus.FAILED : MediaStatus.LOADING, null);
        }

        int size = frames.size();
        int index = (int) (seconds * framerate);
        if (index >= size) {
            if (completed) {
                index %= size;
                state = MediaStatus.COMPLETE;
            } else {
                index = size - 1;
                state = MediaStatus.BUFFERING;
            }
        }
        return new MediaStatus.Pair(state, frames.get(Math.max(0, index)));
    }

    @Override
    public synchronized void close() {
        isClosed = true;
        if (holdsCachedFile) {
            cacheManager.release(uri);
            holdsCachedFile = false;
        }
        if (decoder != null) {
            decoder.stop();
        }
        audio.stopReceiving();
        for (NativeImage frame : frames) {
            frame.close();
        }
        frames.clear();
        completed = false;
    }

    @Override
    public FFmpegWebTexture createTextureView(ResourceLocation resourceLocation) {
        return new FFmpegWebTexture(resourceLocation, this, targetWidth, targetHeight);
    }

    @Override
    public boolean shouldRefreshTexture(IWebTexture tt) {
        return false;
    }
}
