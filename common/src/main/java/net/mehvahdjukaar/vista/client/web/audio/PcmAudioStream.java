package net.mehvahdjukaar.vista.client.web.audio;

import net.minecraft.Util;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

//pulse code modulation stream
public class PcmAudioStream implements AudioStream {

    //about -36 dBFS, quiet but not a pause
    private static final double LOUD_RMS = 520;
    //the engine reads roughly a second at a time so this has to outlast one read
    private static final long LOUD_HOLD_MILLIS = 3000;

    private final PcmAudioSource source;
    private long cursor;
    private volatile long lastLoudMillis = -LOUD_HOLD_MILLIS;

    public PcmAudioStream(PcmAudioSource source, double startSeconds) {
        this.source = source;
        this.cursor = Math.max(0, (long) (startSeconds * PcmAudioSource.SAMPLE_RATE)) * PcmAudioSource.FORMAT.getFrameSize();
    }

    @Override
    public AudioFormat getFormat() {
        return PcmAudioSource.FORMAT;
    }

    @Override
    public ByteBuffer read(int size) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(size);
        cursor = source.readInto(cursor, buffer);
        if (rms(buffer) > LOUD_RMS) {
            lastLoudMillis = Util.getMillis();
        }
        return buffer;
    }

    public boolean wasLoudRecently() {
        return Util.getMillis() - lastLoudMillis < LOUD_HOLD_MILLIS;
    }

    private static double rms(ByteBuffer buffer) {
        ShortBuffer samples = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        int count = samples.remaining();
        if (count == 0) return 0;
        double sumOfSquares = 0;
        for (int i = 0; i < count; i++) {
            short s = samples.get(i);
            sumOfSquares += s * s;
        }
        return Math.sqrt(sumOfSquares / count);
    }

    @Override
    public void close() {
    }
}
