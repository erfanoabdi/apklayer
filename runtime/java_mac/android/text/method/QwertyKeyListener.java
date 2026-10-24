// android.text.method.QwertyKeyListener -- the key listener for a full/qwerty
// hardware keyboard, and the one TextKeyListener delegates to for an ALPHA
// keyboard. Was absent entirely (NoClassDefFoundError). Mirrors the PUBLIC
// surface of frameworks/base/core/java/android/text/method/QwertyKeyListener.java.
//
// ⚠ WHAT IS AND IS NOT HERE. AOSP's onKeyDown is a ~340-line hardware-keyboard
// text engine: auto-capitalisation, auto-text replacement, dead keys, and the
// "backspace right after a correction restores the original" undo built on the
// package-private Replaced marker span. Mac.apk does not route hardware key
// events through KeyListener at all -- text arrives through the IME/host path
// -- so onKeyDown is inherited from BaseKeyListener (false, "not handled"),
// exactly as it would behave on a device with no hardware keyboard attached.
//
// markAsReplaced IS implemented for real, because it is the one piece of that
// engine that is PUBLIC API and that an IME calls from outside: it is how a
// caller tells the listener "this range replaced that original text", and its
// observable effect (a Replaced marker on the range, with any previous one
// removed) is fully reproduced.
package android.text.method;

import android.text.NoCopySpan;
import android.text.Spannable;
import android.text.method.TextKeyListener.Capitalize;

public class QwertyKeyListener extends BaseKeyListener {

    private static QwertyKeyListener[] sInstance =
            new QwertyKeyListener[Capitalize.values().length * 2];
    private static QwertyKeyListener sFullKeyboardInstance;

    private final Capitalize mAutoCap;
    private final boolean mAutoText;
    private final boolean mFullKeyboard;

    private QwertyKeyListener(Capitalize cap, boolean autoText, boolean fullKeyboard) {
        mAutoCap = cap;
        mAutoText = autoText;
        mFullKeyboard = fullKeyboard;
    }

    public QwertyKeyListener(Capitalize cap, boolean autoText) {
        this(cap, autoText, false);
    }

    /** AOSP: a cached instance per (capitalization, autoText) pair. */
    public static QwertyKeyListener getInstance(boolean autoText, Capitalize cap) {
        int off = cap.ordinal() * 2 + (autoText ? 1 : 0);
        if (sInstance[off] == null) {
            sInstance[off] = new QwertyKeyListener(cap, autoText);
        }
        return sInstance[off];
    }

    /** AOSP: the instance for a real full keyboard -- no auto-capitalisation
     *  and no auto-text, because the user is typing deliberately. */
    public static QwertyKeyListener getInstanceForFullKeyboard() {
        if (sFullKeyboardInstance == null) {
            sFullKeyboardInstance = new QwertyKeyListener(Capitalize.NONE, false, true);
        }
        return sFullKeyboardInstance;
    }

    @Override
    public int getInputType() {
        return makeTextContentType(mAutoCap, mAutoText);
    }

    /**
     * AOSP QwertyKeyListener.markAsReplaced(Spannable, int, int, String)
     * (QwertyKeyListener.java:436-449): record that [start, end) replaced
     * `original`, so a following backspace can undo the replacement. Any
     * PREVIOUS Replaced marker on the buffer is removed first -- only the most
     * recent replacement is undoable, which is the behaviour, not an
     * optimisation.
     */
    public static void markAsReplaced(Spannable content, int start, int end, String original) {
        Replaced[] repl = content.getSpans(0, content.length(), Replaced.class);
        for (int a = 0; a < repl.length; a++) {
            content.removeSpan(repl[a]);
        }

        int len = original.length();
        char[] orig = new char[len];
        original.getChars(0, len, orig, 0);

        content.setSpan(new Replaced(orig), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** AOSP QwertyKeyListener.Replaced (package-private there too). */
    /* package */ static class Replaced implements NoCopySpan {
        public Replaced(char[] text) {
            mText = text;
        }

        private char[] mText;

        /* package */ char[] getText() {
            return mText;
        }
    }
}
