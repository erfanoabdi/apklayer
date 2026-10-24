// android.text.method.MetaKeyKeyListener -- AOSP base that tracks meta-key
// (shift / alt / sym) state for the key-listener chain. AOSP:
//   frameworks/base/core/java/android/text/method/MetaKeyKeyListener.java
//   public abstract class MetaKeyKeyListener
// (BaseKeyListener -> NumberKeyListener -> DigitsKeyListener all descend from
// this). Promoted from the v1.0.92 empty stub to the real abstract base so the
// chain compiles against the published meta-state constants + helpers.
//
// Mac.apk routes text input through the IME / InputConnection path, not the
// hardware-key meta-state machine, so the meta tracking is the AOSP-shape
// constants + no-op handlers; the observable contract callers depend on is the
// META_* values and getInputType().
package android.text.method;

import android.text.Editable;
import android.text.Spannable;
import android.view.KeyEvent;
import android.view.View;

public abstract class MetaKeyKeyListener implements KeyListener {
    public static final int META_SHIFT_ON = KeyEvent.META_SHIFT_ON;
    public static final int META_ALT_ON = KeyEvent.META_ALT_ON;
    public static final int META_SYM_ON = KeyEvent.META_SYM_ON;

    public static final long META_CAP_LOCKED = 0x100000000L;
    public static final long META_ALT_LOCKED = 0x200000000L;
    public static final long META_SYM_LOCKED = 0x400000000L;

    protected static final int LOCKED_SHIFT = 8;

    public MetaKeyKeyListener() {}

    public static int getMetaState(CharSequence text) { return 0; }
    public static int getMetaState(CharSequence text, KeyEvent event) {
        return event != null ? event.getMetaState() : 0;
    }
    public static int getMetaState(CharSequence text, int meta) { return 0; }
    public static int getMetaState(CharSequence text, int meta, KeyEvent event) {
        return event != null ? event.getMetaState() : 0;
    }

    // AOSP MetaKeyKeyListener's four private singleton marker spans
    // (MetaKeyKeyListener.java: `private static final Object CAP = new
    // NoCopySpan.Concrete();` and siblings). They are the objects the class
    // uses to record pressed/locked meta state ON the text itself, and
    // isMetaTracker/isSelectingMetaTracker below exist so an Editable's span
    // walker can tell them apart from an app's own spans.
    //
    // ⚠ This tree's meta-state tracking is stubbed (getMetaState answers 0 and
    // resetMetaState is a no-op), so these markers are never actually attached
    // to any text. The two predicates are still exactly right for that state:
    // no object an app holds is ever == one of these, so both answer false,
    // which is the same answer a device gives for a span this class did not
    // create. What they must NOT do is guess from the object's type.
    private static final Object CAP = new android.text.NoCopySpan.Concrete();
    private static final Object ALT = new android.text.NoCopySpan.Concrete();
    private static final Object SYM = new android.text.NoCopySpan.Concrete();
    private static final Object SELECTING = new android.text.NoCopySpan.Concrete();

    /** AOSP MetaKeyKeyListener.isMetaTracker (MetaKeyKeyListener.java:284-287):
     *  "true if this object is one that this class would use to keep track of
     *  any of the meta states in the specified text". */
    public static boolean isMetaTracker(CharSequence text, Object what) {
        return what == CAP || what == ALT || what == SYM || what == SELECTING;
    }

    /** AOSP MetaKeyKeyListener.isSelectingMetaTracker (:293-295). */
    public static boolean isSelectingMetaTracker(CharSequence text, Object what) {
        return what == SELECTING;
    }

    public static void resetMetaState(Spannable text) {}
    public static void adjustMetaAfterKeypress(Spannable content) {}
    public static long adjustMetaAfterKeypress(long state) { return state; }

    public static long resetLockedMeta(long state) { return state; }
    public static void clearMetaKeyState(Editable content, int states) {}
    public static long handleKeyDown(long state, int keyCode, KeyEvent event) { return state; }
    public static long handleKeyUp(long state, int keyCode, KeyEvent event) { return state; }

    @Override
    public void clearMetaKeyState(View view, Editable content, int states) {}

    @Override
    public boolean onKeyDown(View view, Editable content, int keyCode, KeyEvent event) {
        return false;
    }
    @Override
    public boolean onKeyUp(View view, Editable content, int keyCode, KeyEvent event) {
        return false;
    }
    @Override
    public boolean onKeyOther(View view, Editable content, KeyEvent event) {
        return false;
    }
}
