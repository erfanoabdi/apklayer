package android.media.midi;
import java.io.IOException;
/** Receives MIDI data. Mac.apk exposes no MIDI transport, so onSend is never
 *  driven; subclasses still resolve. */
public abstract class MidiReceiver {
    private final int mMaxMessageSize;
    public MidiReceiver() { mMaxMessageSize = Integer.MAX_VALUE; }
    public MidiReceiver(int maxMessageSize) { mMaxMessageSize = maxMessageSize; }
    public abstract void onSend(byte[] msg, int offset, int count, long timestamp) throws IOException;
    public void onFlush() throws IOException {}
    public final int getMaxMessageSize() { return mMaxMessageSize; }
    public void send(byte[] msg, int offset, int count) throws IOException { onSend(msg, offset, count, 0L); }
    public void send(byte[] msg, int offset, int count, long timestamp) throws IOException { onSend(msg, offset, count, timestamp); }
    public void flush() throws IOException { onFlush(); }
}
