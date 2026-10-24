package android.media.midi;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * The base class an app extends to publish its OWN virtual MIDI device (a
 * software synth, a controller bridge). API 23.
 *
 * This half of the MIDI API is app-side, not device-side: the app supplies the
 * receivers and the system binds to the service. Mac.apk has no MidiService to
 * do the binding, so onBind() returns null -- AOSP's own answer for a bind it
 * cannot service -- and getDeviceInfo() reports null because no device record
 * was ever created for this service. The abstract method the subclass must
 * implement is unchanged, so an existing MidiDeviceService subclass compiles and
 * loads exactly as written; it simply never receives a connection.
 *
 * Source: frameworks/base/media/java/android/media/midi/MidiDeviceService.java
 */
public abstract class MidiDeviceService extends Service {

    public static final String SERVICE_INTERFACE = "android.media.midi.MidiDeviceService";

    public MidiDeviceService() { }

    /** AOSP returns the IMidiDeviceServer binder; there is no MidiService to
     *  hand it to here. */
    @Override public IBinder onBind(Intent intent) { return null; }

    /** The receivers the app exposes as this device's INPUT ports. */
    public abstract MidiReceiver[] onGetInputPortReceivers();

    /** AOSP: the receivers the app sends FROM, i.e. this device's output ports.
     *  Empty here because no device record exists to allocate ports against. */
    public final MidiReceiver[] getOutputPortReceivers() { return new MidiReceiver[0]; }

    /** @Nullable in AOSP: null until the system has registered the service as a
     *  device, which never happens on this host. */
    public final MidiDeviceInfo getDeviceInfo() { return null; }

    /** Called when a client's port status changes. Never here. */
    public void onDeviceStatusChanged(MidiDeviceStatus status) { }

    /** Called when the last client disconnects. Never here. */
    public void onClose() { }
}
