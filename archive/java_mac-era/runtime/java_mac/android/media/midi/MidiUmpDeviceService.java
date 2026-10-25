package android.media.midi;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.util.Collections;
import java.util.List;

/**
 * The Universal-MIDI-Packet flavour of {@link MidiDeviceService} (API 35).
 * Same situation, same honest answers -- see that class.
 *
 * Source: frameworks/base/media/java/android/media/midi/MidiUmpDeviceService.java
 */
public abstract class MidiUmpDeviceService extends Service {

    public static final String SERVICE_INTERFACE = "android.media.midi.MidiUmpDeviceService";

    public MidiUmpDeviceService() { }

    @Override public IBinder onBind(Intent intent) { return null; }

    public abstract List<MidiReceiver> onGetInputPortReceivers();

    /** @NonNull in AOSP; empty because no device record exists. */
    public final List<MidiReceiver> getOutputPortReceivers() { return Collections.emptyList(); }

    /** @Nullable in AOSP; null until the system registers the service. */
    public final MidiDeviceInfo getDeviceInfo() { return null; }

    public void onDeviceStatusChanged(MidiDeviceStatus status) { }

    public void onClose() { }
}
