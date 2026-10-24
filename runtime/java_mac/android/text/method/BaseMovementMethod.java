// android.text.method.BaseMovementMethod -- AOSP base class providing
// no-op / false defaults for every MovementMethod member plus the
// protected directional handlers subclasses override. We don't host
// scrolling text editing, so the defaults are the whole behavior.
// AOSP: frameworks/base/core/java/android/text/method/BaseMovementMethod.java
package android.text.method;

import android.text.Spannable;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

public class BaseMovementMethod implements MovementMethod {
    @Override public void initialize(TextView widget, Spannable text) {}
    @Override public boolean onKeyDown(TextView widget, Spannable text,
                                       int keyCode, KeyEvent event) { return false; }
    @Override public boolean onKeyUp(TextView widget, Spannable text,
                                     int keyCode, KeyEvent event) { return false; }
    @Override public boolean onKeyOther(TextView view, Spannable text,
                                        KeyEvent event) { return false; }
    @Override public void onTakeFocus(TextView widget, Spannable text,
                                      int direction) {}
    @Override public boolean onTrackballEvent(TextView widget, Spannable text,
                                              MotionEvent event) { return false; }
    @Override public boolean onTouchEvent(TextView widget, Spannable text,
                                          MotionEvent event) { return false; }
    @Override public boolean onGenericMotionEvent(TextView widget, Spannable text,
                                                  MotionEvent event) { return false; }
    @Override public boolean canSelectArbitrarily() { return false; }

    /**
     * AOSP BaseMovementMethod.getMovementMetaState(Spannable, KeyEvent)
     * (BaseMovementMethod.java:138-143):
     *     int metaState = MetaKeyKeyListener.getMetaState(buffer, event)
     *             & ~(MetaKeyKeyListener.META_ALT_LOCKED | MetaKeyKeyListener.META_SYM_LOCKED);
     *     return KeyEvent.normalizeMetaState(metaState) & ~KeyEvent.META_SHIFT_MASK;
     * -- i.e. the meta state a MOVEMENT decision should use: locked ALT/SYM and
     * SHIFT are deliberately dropped, so shift-arrow reaches the same branch as
     * plain arrow and the SELECTION is what differs.
     *
     * ⚠ KeyEvent.META_SHIFT_MASK is public API on a device but is private in
     * this tree's android.view.KeyEvent (another lane's file), so the mask is
     * spelled out from its AOSP definition:
     *     META_SHIFT_ON | META_SHIFT_LEFT_ON | META_SHIFT_RIGHT_ON
     */
    protected int getMovementMetaState(Spannable buffer, KeyEvent event) {
        final int metaShiftMask =
                KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON | KeyEvent.META_SHIFT_RIGHT_ON;
        int metaState = MetaKeyKeyListener.getMetaState(buffer, event)
                & ~((int) (MetaKeyKeyListener.META_ALT_LOCKED | MetaKeyKeyListener.META_SYM_LOCKED));
        return KeyEvent.normalizeMetaState(metaState) & ~metaShiftMask;
    }

    /**
     * AOSP BaseMovementMethod.previousParagraph(TextView, Spannable)
     * (BaseMovementMethod.java:417-419) -- ctrl-Up. The base implementation
     * returns false; ArrowKeyMovementMethod overrides it. Note it is PUBLIC,
     * unlike the up/down/left/right family, which is protected.
     */
    public boolean previousParagraph(TextView widget, Spannable buffer) { return false; }

    /** AOSP BaseMovementMethod.nextParagraph(TextView, Spannable)
     *  (BaseMovementMethod.java:428-430) -- ctrl-Down. */
    public boolean nextParagraph(TextView widget, Spannable buffer) { return false; }

    protected boolean handleMovementKey(TextView widget, Spannable buffer,
                                        int keyCode, int movementMetaState,
                                        KeyEvent event) { return false; }
    protected boolean left(TextView widget, Spannable buffer) { return false; }
    protected boolean right(TextView widget, Spannable buffer) { return false; }
    protected boolean up(TextView widget, Spannable buffer) { return false; }
    protected boolean down(TextView widget, Spannable buffer) { return false; }
    protected boolean pageUp(TextView widget, Spannable buffer) { return false; }
    protected boolean pageDown(TextView widget, Spannable buffer) { return false; }
    protected boolean top(TextView widget, Spannable buffer) { return false; }
    protected boolean bottom(TextView widget, Spannable buffer) { return false; }
    protected boolean lineStart(TextView widget, Spannable buffer) { return false; }
    protected boolean lineEnd(TextView widget, Spannable buffer) { return false; }
    protected boolean home(TextView widget, Spannable buffer) { return false; }
    protected boolean end(TextView widget, Spannable buffer) { return false; }
}
