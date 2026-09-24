package net.mehvahdjukaar.vista.client.web.audio;

import net.mehvahdjukaar.vista.VistaMod;
import net.mehvahdjukaar.vista.client.web.ffmpeg.FFmpeg;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class FFmpegAudioTrack implements PcmAudioSource {

    private byte[] bytesSamples = new byte[BYTES_PER_SECOND * 8];
    private int byteLength = 0;
    private volatile boolean completed = false;
    private volatile Process ffmpegProcess;

    public void decodeAsync(FFmpeg ffmpeg, Path videoPath, Executor executor) {
        CompletableFuture.runAsync(() -> decode(ffmpeg, videoPath), executor);
    }

    private void decode(FFmpeg ffmpeg, Path videoPath) {
        try {
            ffmpegProcess = ffmpeg.runFFmpeg("-hide_banner", "-loglevel", "error", "-i", videoPath.toString(),
                    "-vn", "-f", "s16le", "-ac", "1", "-ar", String.valueOf(SAMPLE_RATE), "-");
            try (InputStream in = ffmpegProcess.getInputStream()) {
                byte[] chunk = new byte[BYTES_PER_SECOND];
                int read;
                while ((read = in.read(chunk)) > 0) {
                    appendBytes(chunk, read);
                }
            }
            ffmpegProcess.waitFor();
        } catch (Exception e) {
            VistaMod.LOGGER.warn("Failed to decode audio of {}", videoPath, e);
        } finally {
            completed = true;
        }
    }

    private synchronized void appendBytes(byte[] chunk, int count) {
        if (byteLength + count > bytesSamples.length) {
            bytesSamples = Arrays.copyOf(bytesSamples, Math.max(byteLength + count, bytesSamples.length * 2));
        }
        System.arraycopy(chunk, 0, bytesSamples, byteLength, count);
        byteLength += count;
    }

    @Override
    public synchronized boolean isEmpty() {
        return byteLength <= 0;
    }

    @Override
    public synchronized long readInto(long cursor, ByteBuffer destination) {
        int size = destination.remaining();
        int written = 0;
        while (written < size) {
            if (cursor >= byteLength) {
                if (!completed || byteLength == 0) break;
                cursor %= byteLength;
            }
            int count = (int) Math.min(size - written, byteLength - cursor);
            destination.put(written, bytesSamples, (int) cursor, count);
            written += count;
            cursor += count;
        }
        return cursor + (size - written);
    }

    public void stopDecoding() {
        Process p = ffmpegProcess;
        if (p != null) p.destroyForcibly();
    }
}
