package android.media.midi;
import java.io.Closeable;
/** A device's MIDI output port. Mac.apk drives no MIDI, so connect is a no-op.
 *  AOSP: `public final class MidiOutputPort extends MidiSender implements
 *  Closeable` -- connect/disconnect are INHERITED from MidiSender (which
 *  null-checks and forwards to onConnect/onDisconnect), so declaring them here
 *  was both a duplicate and the thing that hid the missing base type. */
public final class MidiOutputPort extends MidiSender implements Closeable {
    private final int mPortNumber;
    MidiOutputPort(int portNumber) { mPortNumber = portNumber; }
    public MidiOutputPort() { mPortNumber = 0; }
    public final int getPortNumber() { return mPortNumber; }
    @Override public void onConnect(MidiReceiver receiver) {}
    @Override public void onDisconnect(MidiReceiver receiver) {}
    @Override public void close() {}
}
