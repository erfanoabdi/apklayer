package android.media.midi;
/** An opened MIDI device. Never produced on Mac.apk. */
public final class MidiDevice implements java.io.Closeable {
    MidiDevice() {}
    public MidiDeviceInfo getInfo() { return null; }
    @Override public void close() { }

    public MidiOutputPort openOutputPort(int portNumber) { return new MidiOutputPort(portNumber); }

    /** AOSP: "Returns a MidiInputPort ... Returns null if the port is already
     *  open." The symmetric counterpart of openOutputPort above, which this
     *  class already hands out. */
    public MidiInputPort openInputPort(int portNumber) { return new MidiInputPort(portNumber); }

    /**
     * AOSP: "Connects the output port of this device to the input port of
     * another device", returning null if the connection could not be made.
     *
     * There is no MIDI transport here (see the class comment), so no data can
     * flow between two ports and null -- AOSP's own "could not connect" answer
     * -- is the honest result. Returning a live-looking MidiConnection would
     * tell an app its ports were wired together when nothing is carried.
     */
    public MidiConnection connectPorts(MidiInputPort inputPort, int outputPortNumber) {
        java.util.Objects.requireNonNull(inputPort, "inputPort must not be null");
        return null;
    }

    /** A live output-to-input link between two devices. Never produced here
     *  (see connectPorts); the type exists so the signature resolves. */
    public class MidiConnection implements java.io.Closeable {
        MidiConnection() { }
        @Override public void close() throws java.io.IOException { }
    }
}

