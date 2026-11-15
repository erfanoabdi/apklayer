package hello.nativeaudio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/** Two processes of the app request MODE_IN_COMMUNICATION (two requesters, as two apps are): the one with a player started owns the mode, the newest
 *  of those when both play (HubCallAudio.owner). The hub logs each owner by pid; tests/vm/native-audio.py reads the order: main, listener, main. */
final class OwnerCheck {
    static AudioTrack track() {
        return new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(48000).setTransferMode(AudioTrack.MODE_STREAM).build();
    }

    static void run(Context context, AudioManager audio) throws Exception {
        AudioTrack mine = track();
        try {
            audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
            mine.play();  // this process plays: it owns the mode
            Listener.command(context, "comm");  // a later requester that plays nothing: no change
            Listener.command(context, "play");  // it plays too, and is newer: it owns the mode
            Listener.command(context, "stop");  // only this process plays again
            System.out.println("[hello-native-audio] OWNER PASS pids main=" + android.os.Process.myPid() + " listener=" + Listener.pid(context));
        } finally {
            mine.stop();
            mine.release();
            audio.setMode(AudioManager.MODE_NORMAL);
            Listener.command(context, "normal");
        }
    }

    private OwnerCheck() {}
}
