// android.media.TimedText -- a single timed-text (subtitle) cue. AOSP
// frameworks/base media/java/android/media/TimedText.java. Mac.apk does not
// produce timed text; this exists so OnTimedTextListener's parameter type
// resolves and apps can read the (empty) cue surface.
package android.media;

public final class TimedText {
    public TimedText() {}
    public String getText() { return null; }
    public android.graphics.Rect getBounds() { return new android.graphics.Rect(); }
}
