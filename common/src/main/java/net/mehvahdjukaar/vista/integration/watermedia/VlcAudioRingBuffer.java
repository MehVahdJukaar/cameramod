package net.mehvahdjukaar.vista.integration.watermedia;

import com.sun.jna.Pointer;
import net.mehvahdjukaar.vista.client.web.audio.PcmAudioSource;
import org.watermedia.api.player.videolan.VideoPlayer;
import org.watermedia.videolan4j.player.base.MediaPlayer;
import org.watermedia.videolan4j.player.base.callback.AudioCallbackAdapter;

import java.nio.ByteBuffer;

//vlc pushes samples in here, the game's sound engine pulls them out
public class VlcAudioRingBuffer extends AudioCallbackAdapter implements PcmAudioSource {

    private static final int RING_BYTES = BYTES_PER_SECOND;
    //slack for jitter
    private static final int PREBUFFER = BYTES_PER_SECOND / 5;

    private final byte[] ring = new byte[RING_BYTES];
    private long written;

    //has to happen before the media starts
    public void attach(VideoPlayer player) {
        var raw = player.raw();
        if (raw != null) {
            raw.mediaPlayer().audio().callback("S16N", SAMPLE_RATE, FORMAT.getChannels(), this, false);
        }
    }

    @Override
    public void play(MediaPlayer mediaPlayer, Pointer samples, int sampleCount, long pts) {
        append(samples.getByteArray(0, sampleCount * FORMAT.getFrameSize()));
    }

    private synchronized void append(byte[] chunk) {
        for (int done = 0; done < chunk.length; ) {
            int head = (int) ((written + done) % RING_BYTES);
            int run = Math.min(chunk.length - done, RING_BYTES - head);
            System.arraycopy(chunk, done, ring, head, run);
            done += run;
        }
        written += chunk.length;
    }

    @Override
    public synchronized boolean isEmpty() {
        return written <= PREBUFFER;
    }

    @Override
    public synchronized long readInto(long cursor, ByteBuffer destination) {
        long oldest = Math.max(0, written - RING_BYTES);
        if (cursor < oldest || cursor > written) cursor = Math.max(oldest, written - PREBUFFER);

        int count = (int) Math.min(destination.remaining(), written - cursor);
        for (int done = 0; done < count; ) {
            int head = (int) ((cursor + done) % RING_BYTES);
            int run = Math.min(count - done, RING_BYTES - head);
            destination.put(done, ring, head, run);
            done += run;
        }
        return cursor + count;
    }
}
