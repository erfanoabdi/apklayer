package hello.nativeaudio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** What WebRTC's audio device module (org.webrtc.audio.WebRtcAudioRecord / WebRtcAudioTrack) does in a call: 10 ms direct ByteBuffers that
 *  must have an array, a mono VOICE_COMMUNICATION AudioRecord at the output rate from the legacy constructor, the platform effects on its
 *  session, the ByteBuffer read and write overloads. Run in MODE_IN_COMMUNICATION (CommCheck). */
final class VoipCheck {
    private static void require(boolean yes, String detail) {
        if (!yes) throw new AssertionError(detail);
    }

    static void run(AudioManager audio) throws Exception {
        int rate = Integer.parseInt(audio.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)), frames = rate / 100;
        ByteBuffer in = ByteBuffer.allocateDirect(frames * 2), out = ByteBuffer.allocateDirect(frames * 2);
        require(in.hasArray() && in.array().length >= frames * 2 && in.arrayOffset() == 0, "direct ByteBuffer without a backing array");
        int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        require(min > 0, "AudioRecord.getMinBufferSize mono " + min);
        AudioRecord record = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 2, in.capacity()));
        int outMin = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack track = new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
                Math.max(outMin, out.capacity()), AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        try {
            require(record.getState() == AudioRecord.STATE_INITIALIZED, "VOICE_COMMUNICATION mono AudioRecord state " + record.getState());
            require(track.getState() == AudioTrack.STATE_INITIALIZED, "VOICE_COMMUNICATION mono AudioTrack state " + track.getState());
            AudioEffect.Descriptor[] effects = AudioEffect.queryEffects();
            int session = record.getAudioSessionId();
            // WebRTC takes the platform's effects when there are some and uses its own otherwise: either is fine, an exception is not
            AcousticEchoCanceler aec = AcousticEchoCanceler.isAvailable() ? AcousticEchoCanceler.create(session) : null;
            NoiseSuppressor ns = NoiseSuppressor.isAvailable() ? NoiseSuppressor.create(session) : null;
            AutomaticGainControl agc = AutomaticGainControl.isAvailable() ? AutomaticGainControl.create(session) : null;
            System.out.println("[hello-native-audio] VOIP rate " + rate + ", min " + min + ", effects " + (effects == null ? -1 : effects.length)
                    + ", aec " + (aec != null) + ", ns " + (ns != null) + ", agc " + (agc != null));
            for (AudioEffect effect : new AudioEffect[] {aec, ns, agc}) if (effect != null) effect.release();
            track.play();
            record.startRecording();
            require(record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING, "recording state");
            long start = System.nanoTime(), level = 0;
            out.order(ByteOrder.LITTLE_ENDIAN);
            in.order(ByteOrder.LITTLE_ENDIAN);
            for (int chunk = 0, n = 0; chunk < 150; chunk++) {  // 1.5 s in 10 ms steps: play a 660 Hz tone (apart from the session capture's 440/880 Hz check), capture the call's input
                out.clear();
                for (int i = 0; i < frames; i++, n++) out.putShort((short) (Math.sin(2 * Math.PI * 660 * n / rate) * 6000));
                out.flip();
                require(track.write(out, out.remaining(), AudioTrack.WRITE_BLOCKING) == frames * 2, "AudioTrack.write(ByteBuffer)");
                int read = record.read(in, in.capacity());
                require(read == in.capacity(), "AudioRecord.read(ByteBuffer) returned " + read);
                if (chunk >= 50) for (int i = 0; i + 1 < read; i += 2) level += Math.abs(in.getShort(i));
            }
            long mean = level / (100L * frames);
            System.out.println("[hello-native-audio] VOIP 1.5 s in " + String.format("%.2f", (System.nanoTime() - start) / 1e9) + " s, mean level " + mean);
            require(mean > 50, "the call's capture is silent: mean level " + mean);
            record.stop();
            System.out.println("[hello-native-audio] VOIP PASS");
        } finally {
            record.release();
            track.stop();
            track.release();
        }
    }

    private VoipCheck() {}
}
