package android.media.midi;

import java.io.Closeable;
import java.io.IOException;

/**
 * A device's MIDI INPUT port -- the endpoint an app SENDS messages to. API 23.
 *
 * Mac.apk exposes no MIDI transport (MidiManager reports no devices), so a port
 * is never handed out by the platform and nothing consumes what is written to
 * one. The type has to exist for MidiDevice.openInputPort/connectPorts to be
 * declarable, and because MidiInputPort IS a MidiReceiver -- an app that holds
 * one passes it wherever a receiver is expected.
 *
 * Source: frameworks/base/media/java/android/media/midi/MidiInputPort.java
 */
public final class MidiInputPort extends MidiReceiver implements Closeable {

    private final int mPortNumber;

    MidiInputPort(int portNumber) { mPortNumber = portNumber; }

    public final int getPortNumber() { return mPortNumber; }

    /** AOSP writes the message to the device's file descriptor. There is no
     *  transport here, so the message is consumed and discarded -- the same
     *  observable result as writing to a device that ignores it. */
    @Override public void onSend(byte[] msg, int offset, int count, long timestamp)
            throws IOException {
        if (msg == null) throw new NullPointerException("msg is null");
        if (offset < 0 || count < 0 || offset + count > msg.length) {
            throw new IllegalArgumentException("offset or count out of range");
        }
    }

    @Override public void close() throws IOException { }
}
