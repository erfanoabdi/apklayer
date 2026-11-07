package hello.nativeaudio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaRecorder;
import java.util.List;

/** What a VoIP app (WebRTC's audio device module) asks before a call: the policy's output and input devices, the communication devices, one of
 *  them selected, then a call's playback and capture in MODE_IN_COMMUNICATION. */
final class CommCheck {
    private static void require(boolean yes, String detail) {
        if (!yes) throw new AssertionError(detail);
    }
    private static boolean has(AudioDeviceInfo[] devices, int type) {
        for (AudioDeviceInfo device : devices) if (device.getType() == type) return true;
        return false;
    }
    private static String list(AudioDeviceInfo[] devices) {
        StringBuilder text = new StringBuilder();
        for (AudioDeviceInfo device : devices) text.append(' ').append(device.getType()).append(':').append(device.getProductName());
        return text.toString();
    }

    static void run(Context context) throws Exception {
        AudioManager audio = context.getSystemService(AudioManager.class);
        AudioDeviceInfo[] outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS), inputs = audio.getDevices(AudioManager.GET_DEVICES_INPUTS);
        List<AudioDeviceInfo> available = audio.getAvailableCommunicationDevices();
        System.out.println("[hello-native-audio] COMM outputs" + list(outputs) + ", inputs" + list(inputs) + ", communication"
                + list(available.toArray(new AudioDeviceInfo[0])) + ", rate " + audio.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
                + ", frames " + audio.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
        require(has(outputs, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) && has(inputs, AudioDeviceInfo.TYPE_BUILTIN_MIC), "speaker and built-in mic");
        require(!available.isEmpty(), "no communication devices");
        for (AudioDeviceInfo device : available) require(device.isSink(), "communication device not a sink");
        require(available.contains(audio.getCommunicationDevice()), "current communication device " + audio.getCommunicationDevice());
        require(Integer.parseInt(audio.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)) > 0, "output sample rate");
        AudioDeviceInfo chosen = available.get(available.size() - 1);
        require(audio.setCommunicationDevice(chosen) && chosen.equals(audio.getCommunicationDevice()), "communication device not selected");
        require(audio.isSpeakerphoneOn() == (chosen.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER), "speakerphone follows the communication device");
        require(preferred().size() == 1 && preferred().get(0).getType() == chosen.getType(), "the policy's preferred call device " + preferred());
        deviceCallback(audio);
        audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
        try {
            require(audio.getMode() == AudioManager.MODE_IN_COMMUNICATION, "mode " + audio.getMode());
            require(phoneState() == AudioManager.MODE_IN_COMMUNICATION, "the policy's phone state " + phoneState());
            audio.setMode(AudioManager.MODE_IN_CALL);  // needs MODIFY_PHONE_STATE, which an app does not get: refused
            require(audio.getMode() == AudioManager.MODE_IN_COMMUNICATION, "MODE_IN_CALL without MODIFY_PHONE_STATE: mode " + audio.getMode());
            RecordCheck.run(AudioAttributes.USAGE_VOICE_COMMUNICATION, MediaRecorder.AudioSource.VOICE_COMMUNICATION, "COMM");
            audio.setMicrophoneMute(true);
            try {
                require(audio.isMicrophoneMute(), "microphone mute not set");
                RecordCheck.run(AudioAttributes.USAGE_VOICE_COMMUNICATION, MediaRecorder.AudioSource.VOICE_COMMUNICATION, "MUTE", true);
            } finally { audio.setMicrophoneMute(false); }
            require(!audio.isMicrophoneMute(), "microphone mute not cleared");
            VoipCheck.run(audio);
        } finally {
            audio.clearCommunicationDevice();
            audio.setMode(AudioManager.MODE_NORMAL);
        }
        require(!audio.isSpeakerphoneOn(), "speakerphone after clearCommunicationDevice");
        require(phoneState() == AudioManager.MODE_NORMAL && preferred().isEmpty(), "the policy after the call: state " + phoneState() + ", preferred " + preferred());
        System.out.println("[hello-native-audio] CALL PASS mode, communication device and microphone mute reach the policy");
    }

    // The policy's own view (hidden APIs, as a system app would read them): IAudioPolicyService.getPhoneState and the call strategy's preferred devices
    private static int phoneState() throws Exception {
        Object binder = Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "media.audio_policy");
        Object policy = Class.forName("android.media.IAudioPolicyService$Stub").getMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
        return ((Number) policy.getClass().getMethod("getPhoneState").invoke(policy)).intValue();
    }
    @SuppressWarnings("unchecked")
    private static List<android.media.AudioDeviceAttributes> preferred() throws Exception {
        Class<?> system = Class.forName("android.media.AudioSystem");
        int strategy = -1;
        for (Object candidate : (List<?>) Class.forName("android.media.audiopolicy.AudioProductStrategy").getMethod("getAudioProductStrategies").invoke(null))
            if (candidate.getClass().getMethod("getAudioAttributesForLegacyStreamType", int.class).invoke(candidate, AudioManager.STREAM_VOICE_CALL) != null) {
                strategy = (int) candidate.getClass().getMethod("getId").invoke(candidate); break;
            }
        List<android.media.AudioDeviceAttributes> devices = new java.util.ArrayList<>();
        system.getMethod("getDevicesForRoleAndStrategy", int.class, int.class, List.class).invoke(null, strategy, 1 /* DEVICE_ROLE_PREFERRED */, devices);
        return devices;
    }
    // AudioManager's device callback: the current devices come first, on the main looper when no handler is given (this is a worker thread)
    private static void deviceCallback(AudioManager audio) throws Exception {
        java.util.concurrent.CountDownLatch added = new java.util.concurrent.CountDownLatch(1);
        Thread[] on = new Thread[1];
        android.media.AudioDeviceCallback callback = new android.media.AudioDeviceCallback() {
            @Override public void onAudioDevicesAdded(AudioDeviceInfo[] devices) { if (devices.length > 0) { on[0] = Thread.currentThread(); added.countDown(); } }
        };
        audio.registerAudioDeviceCallback(callback, null);
        try {
            require(added.await(5, java.util.concurrent.TimeUnit.SECONDS), "no onAudioDevicesAdded");
            require(on[0] == android.os.Looper.getMainLooper().getThread(), "onAudioDevicesAdded on " + on[0] + ", not the main thread");
        } finally { audio.unregisterAudioDeviceCallback(callback); }
    }

    private CommCheck() {}
}
