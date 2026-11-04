package apklayer.host;

import android.media.AudioAttributes;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.shadows.ShadowAudioManager;

/** Java service bookkeeping with real policy volume, devices and global session allocation. */
@Implements(AudioManager.class)
public class ShadowHostNativeAudioManager extends ShadowAudioManager {
    @RealObject private AudioManager real;
    @Implementation @Override protected int getStreamVolume(int stream) {
        if (NativeAudio.isInitializing()) return super.getStreamVolume(stream);
        NativeAudio.ensureInitialized();
        int index = AudioSystem.getStreamVolumeIndex(stream, AudioSystem.DEVICE_OUT_SPEAKER);
        return index >= 0 ? index : super.getStreamVolume(stream);
    }
    // The Android volume stays at its maximum for every stream: the host's volume control (the PipeWire sink) is the one volume of the
    // machine, so an app's own volume keys, sliders and mutes change nothing here (the request is accepted and ignored).
    @Implementation @Override protected void setStreamVolume(int stream, int index, int flags) {
        NativeAudio.ensureInitialized();
    }
    @Implementation @Override protected void adjustStreamVolume(int stream, int direction, int flags) {
        NativeAudio.ensureInitialized();
    }
    @Implementation @Override protected int generateAudioSessionId() {
        NativeAudio.ensureInitialized();
        return AudioSystem.newAudioSessionId();
    }

    // The devices are the real policy's ports (AudioManager's own client code over AudioSystem.listAudioPorts), as on a phone.
    @Implementation @Override public AudioDeviceInfo[] getDevices(int flags) {
        NativeAudio.ensureInitialized();
        return AudioManager.getDevicesStatic(flags);
    }

    // AudioDeviceBroker's communication devices: the sinks of these types (its VALID_COMMUNICATION_DEVICE_TYPES); the current one is the
    // requested one, else the one the policy routes voice communication to, else the earpiece, else the first.
    private static final int[] COMMUNICATION_TYPES = {AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_HEARING_AID, AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_AUX_LINE, AudioDeviceInfo.TYPE_BUS, AudioDeviceInfo.TYPE_BLE_HEARING_AID};
    // shortcut: the request stays in this process and is not set as the policy's preferred device of the communication strategy (that needs
    // MODIFY_AUDIO_ROUTING, system_server's); the policy has one sink today, so the routing is the same. Upgrade when PipeWire offers several sinks.
    private static AudioDeviceAttributes requested;

    @Implementation @Override protected List<AudioDeviceInfo> getAvailableCommunicationDevices() {
        List<AudioDeviceInfo> devices = new ArrayList<>();
        for (AudioDeviceInfo device : getDevices(AudioManager.GET_DEVICES_OUTPUTS))
            for (int type : COMMUNICATION_TYPES) if (device.isSink() && device.getType() == type) devices.add(device);
        return devices;
    }
    @Implementation @Override protected boolean setCommunicationDevice(AudioDeviceInfo device) {
        Objects.requireNonNull(device);
        if (!getAvailableCommunicationDevices().contains(device)) throw new IllegalArgumentException("invalid device type: " + device.getType());
        return change(new AudioDeviceAttributes(device));
    }
    @Implementation @Override protected void clearCommunicationDevice() { change(null); }
    @Implementation @Override protected AudioDeviceInfo getCommunicationDevice() {
        List<AudioDeviceInfo> devices = getAvailableCommunicationDevices();
        AudioDeviceAttributes wanted = requested;
        if (wanted == null) {
            List<AudioDeviceAttributes> routed = AudioSystem.getDevicesForAttributes(
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build(), false);
            wanted = routed.isEmpty() ? null : routed.get(0);
        }
        AudioDeviceInfo earpiece = null;
        for (AudioDeviceInfo device : devices) {
            if (wanted != null && device.getType() == wanted.getType() && device.getAddress().equals(wanted.getAddress())) return device;
            if (earpiece == null && device.getType() == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) earpiece = device;
        }
        return earpiece != null ? earpiece : devices.isEmpty() ? null : devices.get(0);
    }
    private boolean change(AudioDeviceAttributes device) {
        AudioDeviceInfo before = getCommunicationDevice();
        requested = device;
        AudioDeviceInfo after = getCommunicationDevice();
        if (!Objects.equals(before, after)) callOnCommunicationDeviceChangedListeners(after);
        return true;
    }
}
