package apklayer.host;

import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.os.Handler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowAudioManager;
import org.robolectric.util.reflector.Direct;
import org.robolectric.util.reflector.ForType;
import org.robolectric.util.reflector.Reflector;

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
    // The instance's mode and communication device live in the hub (HubCallAudio: AudioService's part, which sets them on the policy); without a
    // hub (a host-only run) they stay in this process.
    private static AudioDeviceAttributes requested;
    private static AudioDeviceAttributes requested() { return HubClient.connected() ? HubClient.current().communicationDevice(false, null) : requested; }

    @Implementation @Override protected void setMode(int mode) {
        super.setMode(HubClient.connected() ? HubClient.current().audioMode(mode) : mode);  // Robolectric's copy dispatches this app's mode listeners
    }
    @Implementation @Override protected int getMode() {
        return HubClient.connected() ? HubClient.current().audioMode(AudioManager.MODE_CURRENT) : super.getMode();
    }
    // AudioService.setMicrophoneMute: a caller without MODIFY_AUDIO_SETTINGS changes nothing; the mute is AudioFlinger's (the HAL silences capture)
    @Implementation @Override protected void setMicrophoneMute(boolean on) {
        android.content.Context context = RuntimeEnvironment.getApplication();
        if (context.checkSelfPermission(android.Manifest.permission.MODIFY_AUDIO_SETTINGS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        NativeAudio.ensureInitialized();
        AudioSystem.muteMicrophone(on);
    }
    @Implementation @Override protected boolean isMicrophoneMute() {
        NativeAudio.ensureInitialized();
        return AudioSystem.isMicrophoneMuted();
    }
    // AudioManager's own registration (the policy's port updates; the first callback on the handler's looper, the main one without a handler)
    @Implementation @Override protected void registerAudioDeviceCallback(AudioDeviceCallback callback, Handler handler) {
        NativeAudio.ensureInitialized();
        Reflector.reflector(AudioManagerDirect.class, real).registerAudioDeviceCallback(callback, handler);
    }
    @Implementation @Override protected void unregisterAudioDeviceCallback(AudioDeviceCallback callback) {
        Reflector.reflector(AudioManagerDirect.class, real).unregisterAudioDeviceCallback(callback);
    }
    @ForType(AudioManager.class)
    interface AudioManagerDirect {
        @Direct void registerAudioDeviceCallback(AudioDeviceCallback callback, Handler handler);
        @Direct void unregisterAudioDeviceCallback(AudioDeviceCallback callback);
    }

    @Implementation @Override protected List<AudioDeviceInfo> getAvailableCommunicationDevices() {
        List<AudioDeviceInfo> devices = new ArrayList<>();
        for (AudioDeviceInfo device : getDevices(AudioManager.GET_DEVICES_OUTPUTS))
            for (int type : COMMUNICATION_TYPES) if (device.isSink() && device.getType() == type) devices.add(device);
        return devices;
    }
    @Implementation @Override protected boolean setCommunicationDevice(AudioDeviceInfo device) {
        Objects.requireNonNull(device);
        boolean valid = false;
        for (int type : COMMUNICATION_TYPES) valid |= device.isSink() && device.getType() == type;
        if (!valid) throw new IllegalArgumentException("invalid device type: " + device.getType());
        return getAvailableCommunicationDevices().contains(device) && change(new AudioDeviceAttributes(device));
    }
    @Implementation @Override protected void clearCommunicationDevice() { change(null); }
    // AudioDeviceBroker.isSpeakerphoneOn: the speaker is the requested communication device; setSpeakerphoneOn requests or drops it.
    @Implementation @Override protected boolean isSpeakerphoneOn() {
        AudioDeviceAttributes device = requested();
        return device != null && device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
    }
    @Implementation @Override protected void setSpeakerphoneOn(boolean on) {
        if (!on) { if (isSpeakerphoneOn()) change(null); return; }
        for (AudioDeviceInfo device : getAvailableCommunicationDevices())
            if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) { change(new AudioDeviceAttributes(device)); return; }
    }
    @Implementation @Override protected AudioDeviceInfo getCommunicationDevice() {
        List<AudioDeviceInfo> devices = getAvailableCommunicationDevices();
        AudioDeviceAttributes wanted = requested();
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
        if (HubClient.connected()) HubClient.current().communicationDevice(true, device); else requested = device;
        AudioDeviceInfo after = getCommunicationDevice();
        if (!Objects.equals(before, after)) callOnCommunicationDeviceChangedListeners(after);
        return true;
    }
}
