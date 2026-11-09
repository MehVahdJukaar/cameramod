package net.mehvahdjukaar.vista.client.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.client.web.audio.FFmpegAudioTrack;
import net.mehvahdjukaar.vista.client.web.ffmpeg.FFmpeg;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class FFmpegMediaDecoder {
    private static final double FALLBACK_FPS = 20;
    private static final int BYTES_PER_PIXEL = 4;

    private final FFmpeg ffmpeg;
    private final String input;
    @Nullable
    private final Path copyTo;
    private volatile boolean stopped;
    @Nullable
    private volatile Process ffmpegProcess;

    public FFmpegMediaDecoder(FFmpeg ffmpeg, String input, @Nullable Path copyTo) {
        this.ffmpeg = ffmpeg;
        this.input = input;
        this.copyTo = copyTo;
    }

    public void stop() {
        stopped = true;
        Process p = ffmpegProcess;
        if (p != null) {
            p.destroyForcibly();
        }
    }

    //this is blocking until ffmpeg is done. returns true if it exited cleanly
    public boolean decode(StreamInfo info, FFmpegAudioTrack audio, Consumer<NativeImage> frameSink, Executor executor) throws IOException, InterruptedException {

        //setup args
        List<String> args = new ArrayList<>();
        if (info.hasVideo()) {
            args.addAll(List.of("-f", "image2pipe", "-vcodec", "rawvideo", "-pix_fmt", "rgba", "-"));
            if (info.hasAudio()) {
                String socketUrl = audio.listenOnLocalSocket(executor);
                args.addAll(FFmpegAudioTrack.createOutputArgs(socketUrl));
            }
        } else {
            args.addAll(FFmpegAudioTrack.createOutputArgs("-"));
        }
        if (copyTo != null) {
            args.addAll(List.of("-map", "0:v?", "-map", "0:a?", "-c", "copy", "-f", "matroska", copyTo.toString()));
        }

        Process process = ffmpeg.decode(input, args);
        ffmpegProcess = process;
        try {
            if (stopped) {
                return false;
            }
            try (InputStream in = new BufferedInputStream(process.getInputStream(), 1024 * 1024)) {
                if (info.hasVideo()) {
                    readFrames(in, info.width(), info.height(), frameSink);
                } else {
                    audio.receive(in);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0 && !stopped) {
                VistaMod.LOGGER.warn("FFmpeg exited with code {} on {}", exitCode, input);
            }
            return exitCode == 0;
        } finally {
            process.destroyForcibly();
        }
    }

    private void readFrames(InputStream in, int width, int height, Consumer<NativeImage> frameSink) throws IOException {
        int bytesPerFrame = width * height * BYTES_PER_PIXEL;
        byte[] data = new byte[bytesPerFrame];
        while (!stopped) {
            if (in.readNBytes(data, 0, bytesPerFrame) < bytesPerFrame) {
                break;
            }
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, width, height, false);
            MemoryUtil.memByteBuffer(img.pixels, bytesPerFrame).put(data);
            frameSink.accept(img);
        }
    }


    public StreamInfo probeStreams() throws IOException {
        //probe args
        List<String> args = new ArrayList<>(List.of("-v", "error", "-show_entries", "stream=codec_type,width,height,r_frame_rate", "-of", "json"));
        args.addAll(FFmpeg.inputArgs(input));

        Process p = ffmpeg.runFFprobe(args.toArray(String[]::new));
        JsonElement json;
        try (Reader r = new InputStreamReader(p.getInputStream())) {
            json = JsonParser.parseReader(r);
        }
        int width = 0, height = 0;
        double framerate = FALLBACK_FPS;
        boolean hasAudio = false;
        if (!json.isJsonObject() || !json.getAsJsonObject().has("streams")) {
            return new StreamInfo(width, height, framerate, false);
        }

        for (JsonElement e : json.getAsJsonObject().getAsJsonArray("streams")) {
            JsonObject stream = e.getAsJsonObject();
            String type = stream.has("codec_type") ? stream.get("codec_type").getAsString() : "";
            if (type.equals("audio")) {
                hasAudio = true;
            } else if (type.equals("video") && width == 0 && stream.has("width") && stream.has("height")) {
                width = stream.get("width").getAsInt();
                height = stream.get("height").getAsInt();
                if (stream.has("r_frame_rate")) {
                    framerate = parseFrameRate(stream.get("r_frame_rate").getAsString());
                }
            }
        }
        if (!Double.isFinite(framerate) || framerate <= 0) {
            VistaMod.LOGGER.warn("Invalid probed framerate {}, defaulting to {} fps", framerate, FALLBACK_FPS);
            framerate = FALLBACK_FPS;
        }
        return new StreamInfo(width, height, framerate, hasAudio);
    }

    private static double parseFrameRate(String rate) {
        String[] parts = rate.split("/");
        if (parts.length == 2) {
            return Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
        }
        return Double.parseDouble(rate);
    }

    public record StreamInfo(int width, int height, double framerate, boolean hasAudio) {
        public boolean hasVideo() {
            return width > 0 && height > 0;
        }
    }


}
