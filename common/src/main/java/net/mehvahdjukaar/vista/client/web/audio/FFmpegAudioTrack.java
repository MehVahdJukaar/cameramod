package net.mehvahdjukaar.vista.client.web.audio;

import net.mehvahdjukaar.vista.VistaMod;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class FFmpegAudioTrack {

    //probably enough
    private static final int SAMPLE_RATE = 24000;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    private static final int BYTES_PER_SECOND = SAMPLE_RATE * FORMAT.getFrameSize();

    private byte[] pcm = new byte[BYTES_PER_SECOND * 8];
    private int length = 0;
    private volatile boolean completed = false;
    private volatile ServerSocket server;

    public AudioFormat getFormat() {
        return FORMAT;
    }

    public long secondsToBytes(double seconds) {
        return Math.max(0, (long) (seconds * SAMPLE_RATE)) * FORMAT.getFrameSize();
    }

    public static List<String> createOutputArgs(String target) {
        return List.of("-map", "0:a:0", "-f", "s16le", "-ac", "1", "-ar", String.valueOf(SAMPLE_RATE), target);
    }

    //returns the socket url. we listen to the main process from this side socket
    public String listenOnLocalSocket(Executor executor) throws IOException {
        ServerSocket socketServer = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        socketServer.setSoTimeout(30000);
        this.server = socketServer;
        CompletableFuture.runAsync(() -> {
            try (socketServer; Socket socket = socketServer.accept(); InputStream in = socket.getInputStream()) {
                receive(in);
            } catch (Exception e) {
                if (!socketServer.isClosed()) {
                    VistaMod.LOGGER.warn("Failed to receive audio from ffmpeg", e);
                }
            }
        }, executor);
        return "tcp://127.0.0.1:" + socketServer.getLocalPort();
    }

    public void receive(InputStream in) throws IOException {
        byte[] chunk = new byte[BYTES_PER_SECOND];
        int read;
        while ((read = in.read(chunk)) > 0) {
            append(chunk, read);
        }
        completed = true;
    }

    private synchronized void append(byte[] chunk, int count) {
        if (length + count > pcm.length) {
            pcm = Arrays.copyOf(pcm, Math.max(length + count, pcm.length * 2));
        }
        System.arraycopy(chunk, 0, pcm, length, count);
        length += count;
    }

    public synchronized boolean isEmpty() {
        return length <= 0;
    }

    public synchronized double getDurationSeconds() {
        return completed ? length / (double) BYTES_PER_SECOND : 0;
    }

    // the cursor always moves by the full buffer
    public synchronized long readInto(long cursor, ByteBuffer destination) {
        long count = Math.min(destination.remaining(), length - cursor);
        if (count > 0) {
            destination.put(0, pcm, (int) cursor, (int) count);
        }
        return cursor + destination.remaining();
    }

    public void stopReceiving() {
        ServerSocket socketServer = server;
        if (socketServer != null) {
            try {
                socketServer.close();
            } catch (IOException ignored) {
            }
        }
    }
}
