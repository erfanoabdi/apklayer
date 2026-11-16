package hello.nativeaaudio;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Bundle;

/** Two apps for one call audio check (tests/vm/audio-uid.py): hello.nativeaaudio plays a VOICE_COMMUNICATION stream natively (AAudio, or OpenSL ES with
 *  -Dhello.api=opensl) and asks for MODE_IN_COMMUNICATION; hello.nativeaaudio2 (the same code) only asks for the mode, later. Neither tells the hub about
 *  a player (no AudioTrack in Java), so the owner of the call mode is told by the audio policy's per-uid or per-process activity (patches/aosp/frameworks/av/0004). */
public class Main extends Activity {
    static { System.loadLibrary("hello"); }
    static native int play(int api);
    static native void stop();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean player = getPackageName().equals("hello.nativeaaudio");
        int api = "opensl".equals(System.getProperty("hello.api")) ? 1 : 0;
        AudioManager audio = getSystemService(AudioManager.class);
        audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
        if (player) {
            int status = play(api);
            System.out.println("[hello-native-aaudio] " + (status == 0 ? "PLAYING " + (api == 0 ? "AAudio" : "OpenSL") : "FAIL play status " + status));
            if (System.getProperty("hello.loop") != null)  // tests/vm/audio-swap.py: new streams over time, played 2 s with 5 s of standby between
                new Thread(() -> {
                    try {
                        while (true) { Thread.sleep(2000); stop(); Thread.sleep(5000); System.out.println("[hello-native-aaudio] AGAIN " + play(api)); }
                    } catch (InterruptedException ended) { }
                }, "replay").start();
        } else System.out.println("[hello-native-aaudio] CALLER mode " + audio.getMode());
    }

    @Override protected void onDestroy() {
        stop();
        getSystemService(AudioManager.class).setMode(AudioManager.MODE_NORMAL);
        super.onDestroy();
    }
}
