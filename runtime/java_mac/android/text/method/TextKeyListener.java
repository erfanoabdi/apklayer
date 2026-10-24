package android.text.method;
import android.text.InputType;
/** The default full-keyboard input handler. Mac.apk routes real key events
 *  through the host, so this holds capitalization/auto-text policy and reports
 *  the input type; it does not itself mutate the buffer. */
// AOSP: `public class TextKeyListener extends BaseKeyListener implements
// SpanWatcher`. The interface was missing along with its three callbacks;
// TextKeyListener attaches itself to the Editable as a SpanWatcher precisely so
// it can drop its "auto-text replacement is still active" marker when the
// selection moves, and an Editable that finds it is NOT a SpanWatcher simply
// never calls it.
public class TextKeyListener extends BaseKeyListener implements android.text.SpanWatcher {
    public enum Capitalize { NONE, SENTENCES, WORDS, CHARACTERS }
    private final Capitalize mAutoCap;
    private final boolean mAutoText;
    private static TextKeyListener sInstance;
    public TextKeyListener(Capitalize cap, boolean autoText) { mAutoCap = cap; mAutoText = autoText; }
    public static TextKeyListener getInstance() {
        if (sInstance == null) sInstance = new TextKeyListener(Capitalize.SENTENCES, true);
        return sInstance;
    }
    public static TextKeyListener getInstance(boolean autoText, Capitalize cap) {
        return new TextKeyListener(cap, autoText);
    }
    @Override public int getInputType() {
        int contentType = InputType.TYPE_CLASS_TEXT;
        switch (mAutoCap) {
            case CHARACTERS: contentType |= InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS; break;
            case WORDS:      contentType |= InputType.TYPE_TEXT_FLAG_CAP_WORDS; break;
            case SENTENCES:  contentType |= InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; break;
            default: break;
        }
        if (mAutoText) contentType |= InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
        return contentType;
    }
    /** AOSP TextKeyListener.ACTIVE (TextKeyListener.java:51) -- the marker span
     *  that records "an auto-text replacement is in progress on this buffer".
     *  Package-private there, and the only one of the four markers that
     *  onSpanChanged below acts on. */
    /* package */ static final Object ACTIVE = new android.text.NoCopySpan.Concrete();

    /** AOSP TextKeyListener.onSpanAdded (TextKeyListener.java:182) -- empty. */
    @Override public void onSpanAdded(android.text.Spannable s, Object what, int start, int end) { }

    /** AOSP TextKeyListener.onSpanRemoved (TextKeyListener.java:183) -- empty. */
    @Override public void onSpanRemoved(android.text.Spannable s, Object what, int start, int end) { }

    /**
     * AOSP TextKeyListener.onSpanChanged (TextKeyListener.java:185-190):
     *     if (what == Selection.SELECTION_END) { s.removeSpan(ACTIVE); }
     * -- moving the caret cancels an in-progress auto-text replacement, which
     * is what stops a later backspace from undoing a correction the user has
     * already navigated away from.
     */
    @Override public void onSpanChanged(android.text.Spannable s, Object what,
            int start, int end, int st, int en) {
        if (what == android.text.Selection.SELECTION_END) {
            s.removeSpan(ACTIVE);
        }
    }

    /**
     * AOSP TextKeyListener.shouldCap(Capitalize, CharSequence, int)
     * (TextKeyListener.java:117-130): NONE never capitalizes, CHARACTERS always
     * does, and WORDS/SENTENCES defer to TextUtils.getCapsMode with the matching
     * CAP_MODE_* bit.
     */
    public static boolean shouldCap(Capitalize cap, CharSequence cs, int off) {
        if (cap == Capitalize.NONE) {
            return false;
        }
        if (cap == Capitalize.CHARACTERS) {
            return true;
        }
        return android.text.TextUtils.getCapsMode(cs, off,
                cap == Capitalize.WORDS ? android.text.TextUtils.CAP_MODE_WORDS
                                        : android.text.TextUtils.CAP_MODE_SENTENCES) != 0;
    }

    public void release() {}
    public static void clear(android.text.Editable e) { e.clear(); }
}
