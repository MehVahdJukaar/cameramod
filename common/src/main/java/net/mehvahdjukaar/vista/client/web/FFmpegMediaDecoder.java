package net.mehvahdjukaar.vista.client.web;

import com.mojang.blaze3d.platform.NativeImage;
import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.client.web.ffmpeg.FFmpeg;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes a local video file into a FrameBuffer (one frame at a time).
 * Supports pausing and seeking (by restarting FFmpeg at a new timestamp).
 * Runs in its own thread.
 */
public class FFmpegMediaDecoder {

    private final FFmpeg ffmpeg;
    private final FFmpegMediaSession buffer;
    private final Path videoPath;
    private volatile boolean running = true;
    private volatile boolean paused = false;
    private volatile double seekToSeconds = -1.0; // -1 means no seek requested


    public FFmpegMediaDecoder(FFmpeg ffmpeg, FFmpegMediaSession buffer, Path videoPath) {
        this.ffmpeg = ffmpeg;
        this.buffer = buffer;
        this.videoPath = videoPath;
    }

    /**
     * Pause decoding (stops reading new frames).
     */
    public void pause() {
        paused = true;
        VistaMod.LOGGER.info("Paused");
    }

    /**
     * Resume decoding.
     */
    public void resumePlay() {
        paused = false;
        synchronized (this) {
            notifyAll(); // wake up waiting thread
        }
        VistaMod.LOGGER.info("Resumed");
    }

    /**
     * Seek to a specific time (seconds). The decoder will restart at that timestamp.
     */
    public void seek(double seconds) {
        this.seekToSeconds = seconds;
        // Interrupt the current FFmpeg process gently
        // The main loop will detect seekToSeconds != -1 and restart
        VistaMod.LOGGER.info("Seek requested to {}s", seconds);
    }

    /**
     * Stop the decoder completely.
     */
    public void stopDecoder() {
        running = false;
        resumePlay(); // wake up if paused
        VistaMod.LOGGER.info("Stopping decoder");
    }

    public void run() {
        while (running) {
            Process ffmpegProcess = null;
            try {
                // Probe video metadata (size, framerate, total frames)
                int[] size = probeVideoSize(videoPath.toString());
                int width = size[0], height = size[1];
                int frameSize = width * height * 3;
                double framerate = probeFrameRate(videoPath.toString());
                if (!Double.isFinite(framerate) || framerate <= 0) {
                    VistaMod.LOGGER.warn("Invalid probed framerate {}, defaulting to 20 fps", framerate);
                    framerate = 20.0;
                }
                int totalFrames = probeTotalFrames(videoPath.toString()); // may be -1
                VistaMod.LOGGER.info("Video: {}x{}, {} fps, {} frames", width, height, framerate, totalFrames);

                // Build FFmpeg command, with seek if requested
                String[] cmd = buildFFmpegCommand(seekToSeconds);
                ffmpegProcess = ffmpeg.runFFmpeg(cmd);
                if (seekToSeconds >= 0) {
                    VistaMod.LOGGER.info("Seeked to {}s, restarting FFmpeg", seekToSeconds);
                    seekToSeconds = -1.0; // reset
                }

                int frameCount = 0;
                try (InputStream in = new BufferedInputStream(ffmpegProcess.getInputStream(), 1024 * 1024)) {
                    while (running && !Thread.interrupted()) {
                        // Pause handling
                        synchronized (this) {
                            while (paused && running) {
                                VistaMod.LOGGER.info("Decoder waiting (paused)");
                                wait();
                            }
                        }
                        if (!running) break;

                        // Check if a new seek was requested while we were decoding
                        if (seekToSeconds >= 0) {
                            VistaMod.LOGGER.info("Seek request detected, restarting FFmpeg");
                            ffmpegProcess.destroyForcibly();
                            ffmpegProcess.waitFor();
                            break; // break out of inner loop, outer loop will restart with new seek
                        }

                        // Read one frame's raw RGB24 data
                        byte[] data = in.readNBytes(frameSize);
                        if (data.length < frameSize) {
                            //end of data
                            break;
                        }

                        double pts = frameCount / framerate;
                        NativeImage rawImg = rgbBytesToNativeImage(width, height, data);

                        buffer.add(new MediaFrame(rawImg, pts));
                        frameCount++;

                        if (frameCount % 100 == 0) {
                            VistaMod.LOGGER.info("Decoded {} frames, PTS={}", frameCount, String.format("%.2f", pts));
                        }
                    }
                }

                int exitCode = ffmpegProcess.waitFor();
                if (exitCode != 0 && running) {
                    VistaMod.LOGGER.warn("FFmpeg exited with code {}", exitCode);
                }
                if (frameCount > 0 && seekToSeconds < 0) {
                    // natural end of file
                    VistaMod.LOGGER.info("Finished decoding, total frames: {}", frameCount);
                    buffer.setCompleted(true);
                    break;
                }
            } catch (InterruptedException e) {
                VistaMod.LOGGER.info("Decoder interrupted");
                break;
            } catch (Exception e) {
                VistaMod.LOGGER.error("Decoder crash: {}", e.getMessage(), e);
                break;
            } finally {
                if (ffmpegProcess != null) ffmpegProcess.destroyForcibly();
            }
        }
        VistaMod.LOGGER.info("Decoder thread terminated");
    }

    private String[] buildFFmpegCommand(double seekSec) {
        List<String> args = new ArrayList<>();
        args.add("-hide_banner");
        args.add("-loglevel");
        args.add("error");
        if (seekSec >= 0) {
            args.add("-ss");
            args.add(String.valueOf(seekSec));
        }
        args.add("-i");
        args.add(videoPath.toString());
        args.add("-f");
        args.add("image2pipe");
        args.add("-vcodec");
        args.add("rawvideo");
        args.add("-pix_fmt");
        args.add("rgb24");
        args.add("-");
        return args.toArray(new String[0]);
    }

    // ---------- FFprobe helpers ----------
    private double probeFrameRate(String path) throws IOException {
        Process p = ffmpeg.runFFprobe("-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=r_frame_rate", "-of", "csv=p=0", path);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line = r.readLine();
            if (line != null && line.contains("/")) {
                String[] parts = line.trim().split("/");
                double num = Double.parseDouble(parts[0]);
                double den = Double.parseDouble(parts[1]);
                if (den == 0) {
                    return 20.0;
                }
                return num / den;
            } else if (line != null && !line.isEmpty()) {
                return Double.parseDouble(line);
            }
        }
        VistaMod.LOGGER.warn("Could not probe framerate, defaulting to 30 fps");
        return 30.0;
    }

    private int probeTotalFrames(String path) throws IOException {
        Process p = ffmpeg.runFFprobe("-v", "error", "-select_streams", "v:0",
                "-count_frames", "-show_entries", "stream=nb_read_frames", "-of", "csv=p=0", path);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line = r.readLine();
            if (line != null && !line.isEmpty()) {
                return Integer.parseInt(line.trim());
            }
        }
        return -1;
    }

    private int[] probeVideoSize(String path) throws IOException {
        Process p = ffmpeg.runFFprobe("-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=width,height", "-of", "csv=s=x:p=0", path);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line = r.readLine();
            if (line == null || line.trim().isEmpty()) {
                throw new IOException("Empty ffprobe output for " + path);
            }
            String[] parts = line.trim().split("x");
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        }
    }

    private NativeImage rgbBytesToNativeImage(int width, int height, byte[] data) {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, width, height, true);
        for (int y = 0, p = 0; y < height; y++) {
            for (int x = 0; x < width; x++, p += 3) {
                int r = data[p] & 0xFF;
                int g = data[p + 1] & 0xFF;
                int b = data[p + 2] & 0xFF;
                int abgr = 0xFF000000 | (b << 16) | (g << 8) | r;
                img.setPixelRGBA(x, y, abgr);
            }
        }
        return img;
    }

}
