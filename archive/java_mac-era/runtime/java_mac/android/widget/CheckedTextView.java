// android.widget.CheckedTextView -- a TextView that maintains a checked
// state (used by ListView single/multi-choice rows and NavigationView). Was
// a bare `extends Object` stub, which broke instanceof TextView/Checkable and
// every choice-mode list row. Now the real TextView + Checkable. AOSP:
//   frameworks/base/core/java/android/widget/CheckedTextView.java
//   public class CheckedTextView extends TextView implements Checkable
package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;

public class CheckedTextView extends TextView implements Checkable {
    private boolean mChecked;
    private Drawable mCheckMarkDrawable;
    private int mCheckMarkResource;
    private int mCheckMarkGravity = android.view.Gravity.END;

    public CheckedTextView(Context context) { super(context); }
    public CheckedTextView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }
    public CheckedTextView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    public CheckedTextView(Context context, android.util.AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);
    }

    @Override public void toggle() { setChecked(!mChecked); }
    @Override public boolean isChecked() { return mChecked; }
    @Override public void setChecked(boolean checked) {
        if (mChecked != checked) {
            mChecked = checked;
            refreshDrawableState();
            invalidate();
        }
    }

    public void setCheckMarkDrawable(Drawable d) { mCheckMarkDrawable = d; }
    public void setCheckMarkDrawable(int resId) { mCheckMarkResource = resId; }
    public Drawable getCheckMarkDrawable() { return mCheckMarkDrawable; }
    // AOSP CheckedTextView.setCheckMarkTintList/getCheckMarkTintList (API 21)
    // was `{}` / hardcoded null with no backing field -- the same half-
    // implemented-accessor shape as ProgressBar's drawable accessors, and
    // inconsistent with its own sibling setCheckMarkTintMode/
    // getCheckMarkTintMode two lines below, which already round-trip through
    // a real field. A caller that sets tint list + tint mode together (the
    // common pattern; see CompoundButton.applyButtonTint / ImageView.
    // applyImageTint in this tree) had the list half of that call silently
    // discarded.
    private android.content.res.ColorStateList mCheckMarkTintList;
    public void setCheckMarkTintList(android.content.res.ColorStateList tint) { mCheckMarkTintList = tint; }
    public android.content.res.ColorStateList getCheckMarkTintList() { return mCheckMarkTintList; }
    public void setCheckMarkGravity(int gravity) { mCheckMarkGravity = gravity; }
    public int  getCheckMarkGravity() { return mCheckMarkGravity; }

    private android.graphics.PorterDuff.Mode mCheckMarkTintMode;
    /** AOSP setCheckMarkTintMode (API 21): blend mode for the check-mark tint. */
    public void setCheckMarkTintMode(android.graphics.PorterDuff.Mode tintMode) {
        mCheckMarkTintMode = tintMode;
    }
    public android.graphics.PorterDuff.Mode getCheckMarkTintMode() { return mCheckMarkTintMode; }

    /** AOSP CheckedTextView.onCreateDrawableState (android11-release
     *  CheckedTextView.java:466-472): merge CHECKED_STATE_SET when checked.
     *  setChecked() above already called refreshDrawableState() but this
     *  override did not exist, so state_checked never reached the state
     *  array and every choice-mode row selector stayed on its unchecked
     *  entry (fwprobe DRAWSTATE.CheckedTextView.checked: golden
     *  `checked,enabled,multiline`, ours had no `checked`). */
    private static final int[] MACAPK_CHECKED_STATE_SET = { 0x010100a0 };

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isChecked()) {
            android.view.View.mergeDrawableStates(drawableState, MACAPK_CHECKED_STATE_SET);
        }
        return drawableState;
    }

    // ---- API-36 surface completion: the BlendMode half of the check-mark tint
    // pair (AOSP CheckedTextView.setCheckMarkTintBlendMode /
    // getCheckMarkTintBlendMode). Same storage arrangement as the sibling
    // widgets: the PorterDuff.Mode field above stays the applied value, the
    // BlendMode is the exact round-trip value, converted via MacApkBlendModes.
    private android.graphics.BlendMode mCheckMarkBlendMode;
    public void setCheckMarkTintBlendMode(android.graphics.BlendMode blendMode) {
        mCheckMarkBlendMode = blendMode;
        setCheckMarkTintMode(MacApkBlendModes.toPorterDuff(blendMode));
    }
    public android.graphics.BlendMode getCheckMarkTintBlendMode() { return mCheckMarkBlendMode; }
}
