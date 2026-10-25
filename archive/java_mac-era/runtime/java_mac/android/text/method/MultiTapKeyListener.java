// android.text.method.MultiTapKeyListener -- the key listener for a 12-key
// phone keypad, where tapping '2' repeatedly cycles a/b/c. Was absent entirely
// (NoClassDefFoundError); TextKeyListener names it as the listener it delegates
// to for a NUMERIC keyboard, so the reference has to resolve. Mirrors the
// PUBLIC surface of
// frameworks/base/core/java/android/text/method/MultiTapKeyListener.java.
//
// ⚠ Same scope note as QwertyKeyListener: AOSP's onKeyDown implements the
// timed tap-cycling itself, and Mac.apk does not route hardware key events
// through KeyListener at all (text arrives through the IME/host path), so
// onKeyDown is inherited from BaseKeyListener. The SpanWatcher half IS
// implemented for real -- it is what cancels an in-progress tap cycle when the
// caret moves, and it is reachable from outside because the listener installs
// itself on the Editable.
package android.text.method;

import android.text.Selection;
import android.text.Spannable;
import android.text.SpanWatcher;
import android.text.method.TextKeyListener.Capitalize;

public class MultiTapKeyListener extends BaseKeyListener implements SpanWatcher {

    private static MultiTapKeyListener[] sInstance =
            new MultiTapKeyListener[Capitalize.values().length * 2];

    private final Capitalize mCapitalize;
    private final boolean mAutoText;

    public MultiTapKeyListener(Capitalize cap, boolean autotext) {
        mCapitalize = cap;
        mAutoText = autotext;
    }

    /** AOSP: a cached instance per (capitalization, autoText) pair. */
    public static MultiTapKeyListener getInstance(boolean autotext, Capitalize cap) {
        int off = cap.ordinal() * 2 + (autotext ? 1 : 0);
        if (sInstance[off] == null) {
            sInstance[off] = new MultiTapKeyListener(cap, autotext);
        }
        return sInstance[off];
    }

    @Override
    public int getInputType() {
        return makeTextContentType(mCapitalize, mAutoText);
    }

    /** AOSP MultiTapKeyListener.onSpanAdded -- empty. */
    @Override
    public void onSpanAdded(Spannable s, Object what, int start, int end) {
    }

    /** AOSP MultiTapKeyListener.onSpanRemoved -- empty. */
    @Override
    public void onSpanRemoved(Spannable s, Object what, int start, int end) {
    }

    /**
     * AOSP MultiTapKeyListener.onSpanChanged (MultiTapKeyListener.java:238-244):
     * moving the caret ends the current tap cycle, so the next press of the
     * same key starts a NEW character instead of cycling the previous one.
     */
    @Override
    public void onSpanChanged(Spannable buf, Object what, int s, int e, int start, int stop) {
        if (what == Selection.SELECTION_END) {
            buf.removeSpan(TextKeyListener.ACTIVE);
        }
    }
}
