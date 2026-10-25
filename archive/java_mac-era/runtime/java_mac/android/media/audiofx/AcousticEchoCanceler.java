// android.media.audiofx.AcousticEchoCanceler -- mic echo cancellation
// preprocessor. AOSP frameworks/base media/java/android/media/audiofx/AcousticEchoCanceler.java.
// Mac.apk has no mic source/preprocessor: isAvailable false, create returns null.
package android.media.audiofx;

public class AcousticEchoCanceler extends AudioEffect {
    private AcousticEchoCanceler(int audioSession) {
        super(EFFECT_TYPE_NULL, EFFECT_TYPE_NULL, 0, audioSession);
    }
    public static boolean isAvailable() { return false; }
    public static AcousticEchoCanceler create(int audioSession) { return null; }
}
