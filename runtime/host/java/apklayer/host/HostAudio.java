package apklayer.host;

import android.media.AudioFormat;
import android.media.AudioTrack;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.WeakHashMap;
import org.robolectric.shadows.ShadowAudioTrack;

/**
 * AudioTrack output (PLAN P5.2). Robolectric's AudioTrack shadow accepts every write and reports it to
 * listeners; this one streams the PCM to the desktop's audio server with pw-cat (one process per track).
 * The write blocks when the pipe is full, which paces a streaming app like a real AudioTrack.
 */
final class HostAudio {
    private HostAudio() {}

    private static final Map<AudioTrack, Process> players = new WeakHashMap<>();

    static void install() {
        ShadowAudioTrack.addAudioDataListener(HostAudio::write);
    }

    private static void write(AudioTrack track, byte[] data, AudioFormat format) {
        try {
            Process p = players.get(track);
            if (p == null) {
                int enc = format.getEncoding();
                String fmt = enc == AudioFormat.ENCODING_PCM_8BIT ? "u8" : enc == AudioFormat.ENCODING_PCM_FLOAT ? "f32" : "s16";
                p = new ProcessBuilder("pw-cat", "-p", "--rate=" + format.getSampleRate(),
                        "--channels=" + Math.max(1, format.getChannelCount()), "--format=" + fmt, "-")
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.INHERIT).start();
                players.put(track, p);
                System.out.println("[apklayer] audio track " + format.getSampleRate() + " Hz, " + format.getChannelCount() + " ch, " + fmt);
            }
            OutputStream out = p.getOutputStream();
            out.write(data);
            out.flush();
        } catch (IOException e) {
            players.remove(track); // the player went away (or never started): drop the sound, keep the app running
        }
    }
}
