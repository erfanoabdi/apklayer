// android.media.audiofx.NoiseSuppressor -- mic noise suppression preprocessor.
// AOSP frameworks/base media/java/android/media/audiofx/NoiseSuppressor.java.
// Mac.apk has no mic source/preprocessor, so isAvailable reports false and
// create returns null (AOSP returns null when the effect cannot be
// instantiated) -- the caller's "if (NoiseSuppressor.isAvailable())" guard
// simply skips it.
package android.media.audiofx;

public class NoiseSuppressor extends AudioEffect {
    private NoiseSuppressor(int audioSession) {
        super(EFFECT_TYPE_NULL, EFFECT_TYPE_NULL, 0, audioSession);
    }
    public static boolean isAvailable() { return false; }
    public static NoiseSuppressor create(int audioSession) { return null; }
}
