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
        audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
        try {
            require(audio.getMode() == AudioManager.MODE_IN_COMMUNICATION, "mode " + audio.getMode());
            RecordCheck.run(AudioAttributes.USAGE_VOICE_COMMUNICATION, MediaRecorder.AudioSource.VOICE_COMMUNICATION, "COMM");
        } finally {
            audio.clearCommunicationDevice();
            audio.setMode(AudioManager.MODE_NORMAL);
        }
    }

    private CommCheck() {}
}
