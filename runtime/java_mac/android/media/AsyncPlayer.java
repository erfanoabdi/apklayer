// android.media.AsyncPlayer -- a helper that plays a sound from a Uri on a
// background thread (used by notifications/alarms). AOSP frameworks/base
// media/java/android/media/AsyncPlayer.java.
//
// Mac.apk backs this with MediaPlayer where the Uri is a decodable file; play()
// stops any prior playback and starts the new sound, stop() halts it. A Uri we
// cannot resolve/decode falls through to a no-op (no crash) so notification
// posting still proceeds.
package android.media;

// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class AsyncPlayer {
    private final String mTag;
    private MediaPlayer mPlayer;

    public AsyncPlayer(String tag) { mTag = tag; }

    public void play(android.content.Context context, android.net.Uri uri,
                     boolean looping, int stream) {
        stop();
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(context, uri);
            mp.setLooping(looping);
            mp.prepare();
            mp.start();
            mPlayer = mp;
        } catch (Throwable ignore) {
            mPlayer = null;
        }
    }
    public void play(android.content.Context context, android.net.Uri uri,
                     boolean looping, AudioAttributes attributes) {
        play(context, uri, looping, AudioManager.STREAM_MUSIC);
    }

    public void stop() {
        MediaPlayer mp = mPlayer;
        mPlayer = null;
        if (mp != null) {
            try { mp.stop(); } catch (Throwable ignore) {}
            try { mp.release(); } catch (Throwable ignore) {}
        }
    }
}
