package android.media.midi;
import android.os.Handler;
/** MIDI device access (API 23). Mac.apk exposes no MIDI devices, so the list is
 *  empty and callbacks never fire. Was a loose-Object stub; now typed. */
public final class MidiManager {
    public static final int TRANSPORT_MIDI_BYTE_STREAM = 1;
    public static final int TRANSPORT_UNIVERSAL_MIDI_PACKETS = 2;
    public MidiManager() {}
    public MidiManager(android.content.Context context) {}
    public MidiDeviceInfo[] getDevices() { return new MidiDeviceInfo[0]; }
    public java.util.Set<MidiDeviceInfo> getDevicesForTransport(int transport) { return java.util.Collections.emptySet(); }
    public void openDevice(MidiDeviceInfo deviceInfo, OnDeviceOpenedListener listener, Handler handler) { }
    public void openBluetoothDevice(android.bluetooth.BluetoothDevice bluetoothDevice, OnDeviceOpenedListener listener, Handler handler) { }
    public void registerDeviceCallback(DeviceCallback callback, Handler handler) { }
    public void registerDeviceCallback(int transport, java.util.concurrent.Executor executor, DeviceCallback callback) { }
    public void unregisterDeviceCallback(DeviceCallback callback) { }
    public interface OnDeviceOpenedListener { void onDeviceOpened(MidiDevice device); }
    public static class DeviceCallback {
        public void onDeviceAdded(MidiDeviceInfo device) { }
        public void onDeviceRemoved(MidiDeviceInfo device) { }
        public void onDeviceStatusChanged(MidiDeviceStatus status) { }
    }
}
