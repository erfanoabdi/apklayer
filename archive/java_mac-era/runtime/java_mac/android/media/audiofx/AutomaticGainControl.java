// android.media.audiofx.AutomaticGainControl -- mic AGC preprocessor. AOSP
// frameworks/base media/java/android/media/audiofx/AutomaticGainControl.java.
// Mac.apk has no mic source/preprocessor: isAvailable false, create returns null.
package android.media.audiofx;

public class AutomaticGainControl extends AudioEffect {
    private AutomaticGainControl(int audioSession) {
        super(EFFECT_TYPE_NULL, EFFECT_TYPE_NULL, 0, audioSession);
    }
    public static boolean isAvailable() { return false; }
    public static AutomaticGainControl create(int audioSession) { return null; }
}
