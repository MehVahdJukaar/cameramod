package net.mehvahdjukaar.vista.client.web.audio;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

//shared interface so watermedia and ffmepg work together
//shared across tvs. only state is audio itself, stream holds the cursors
public interface PcmAudioSource {

    //probably enough
    int SAMPLE_RATE = 24000;
    int BYTES_PER_SECOND = SAMPLE_RATE * 2;
    AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

    boolean isEmpty();

    long readInto(long cursor, ByteBuffer destination);
}
