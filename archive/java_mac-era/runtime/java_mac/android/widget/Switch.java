// android.widget.Switch -- the slider-toggle widget. Subclass of
// CompoundButton. androidx.preference.SwitchPreferenceCompat falls
// back to this when androidx.appcompat.widget.SwitchCompat isn't
// available.
package android.widget;

import android.content.Context;
import android.util.AttributeSet;

public class Switch extends CompoundButton {
    // AOSP stores these labels; they back getTextOn/Off so a host querying
    // the configured strings (e.g. for accessibility) reads them back.
    private CharSequence mTextOn = "";
    private CharSequence mTextOff = "";
    private boolean mShowText;
    private boolean mSplitTrack;
    private int mSwitchPadding;
    private int mThumbTextPadding;
    private android.graphics.drawable.Drawable mThumbDrawable;
    private android.graphics.drawable.Drawable mTrackDrawable;

    // AOSP Switch (android16-release core/java/android/widget/Switch.java:
    // 200-227) chains (Context)/(Context,AttributeSet) down to
    // this(context, attrs, com.android.internal.R.attr.switchStyle) --
    // 0x0101043f, verified against public-final.xml. Same CompoundButton gap
    // as CheckBox/RadioButton: a programmatic `new Switch(ctx)` used to get
    // defStyleAttr=0, i.e. no default style at all.
    private static final int SWITCH_STYLE = 0x0101043f;
    public Switch(Context context) { this(context, null); }
    public Switch(Context context, AttributeSet attrs) { this(context, attrs, SWITCH_STYLE); }
    public Switch(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }
    public Switch(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) { super(context, attrs, defStyleAttr); }

    public void setTextOn(CharSequence textOn) { mTextOn = textOn; }
    public CharSequence getTextOn() { return mTextOn; }
    public void setTextOff(CharSequence textOff) { mTextOff = textOff; }
    public CharSequence getTextOff() { return mTextOff; }
    public void setShowText(boolean showText) { mShowText = showText; }
    public boolean getShowText() { return mShowText; }
    public void setSplitTrack(boolean splitTrack) { mSplitTrack = splitTrack; }
    public boolean getSplitTrack() { return mSplitTrack; }
    public void setSwitchPadding(int pixels) { mSwitchPadding = pixels; }
    public int  getSwitchPadding() { return mSwitchPadding; }
    public void setThumbTextPadding(int pixels) { mThumbTextPadding = pixels; }
    public int  getThumbTextPadding() { return mThumbTextPadding; }
    // AOSP Switch.setSwitchMinWidth/getSwitchMinWidth was `{}` / hardcoded 0
    // with no backing field -- half-implemented-accessor shape, found by the
    // same grep that caught the tint-list pair below.
    private int mSwitchMinWidth;
    public void setSwitchMinWidth(int pixels) { mSwitchMinWidth = pixels; }
    public int  getSwitchMinWidth() { return mSwitchMinWidth; }
    public void setThumbDrawable(android.graphics.drawable.Drawable thumb) { mThumbDrawable = thumb; }
    // AOSP Switch.setThumbResource(int) resolves the resource through the
    // Context and delegates to setThumbDrawable, same as CompoundButton.
    // setButtonDrawable(int) elsewhere in this package. Was a pure no-op, so
    // getThumbDrawable() stayed stale/null after a resource-id call.
    public void setThumbResource(int resId) {
        setThumbDrawable(resId != 0 ? getContext().getDrawable(resId) : null);
    }
    public void setTrackDrawable(android.graphics.drawable.Drawable track) { mTrackDrawable = track; }
    public void setTrackResource(int resId) {
        setTrackDrawable(resId != 0 ? getContext().getDrawable(resId) : null);
    }
    public android.graphics.drawable.Drawable getThumbDrawable() { return mThumbDrawable; }
    public android.graphics.drawable.Drawable getTrackDrawable() { return mTrackDrawable; }
    // AOSP Switch.setThumbTintList/getThumbTintList and setTrackTintList/
    // getTrackTintList (API 21+) were `{}` / hardcoded null with no backing
    // field -- the exact ProgressBar-drawable-accessor shape, sitting right
    // next to getThumbDrawable/getTrackDrawable above which already round-
    // trip correctly through real fields.
    private android.content.res.ColorStateList mThumbTintList;
    private android.content.res.ColorStateList mTrackTintList;
    public void setThumbTintList(android.content.res.ColorStateList tint) { mThumbTintList = tint; }
    public android.content.res.ColorStateList getThumbTintList() { return mThumbTintList; }
    public void setTrackTintList(android.content.res.ColorStateList tint) { mTrackTintList = tint; }
    public android.content.res.ColorStateList getTrackTintList() { return mTrackTintList; }
    public void setSwitchTypeface(android.graphics.Typeface tf) {}

    /** AOSP Switch.setSwitchTextAppearance (frameworks/base/.../widget/Switch.java):
     *  reads a TextAppearance style resource and applies its textColor / textSize /
     *  typeface / textAllCaps to the switch's OWN internally-painted "ON"/"OFF"
     *  track labels (mTextColors, mTextPaint, mSwitchTransformationMethod --
     *  fields that back Switch's custom onDraw, not any CompoundButton/TextView
     *  text). This Switch, like the rest of this file (setThumbDrawable/
     *  setTrackDrawable/the tint-list pairs above), is a drawable-and-tint compat
     *  shim: it has no onDraw override and no mTextPaint/mTextColors of its own,
     *  so there is no pixel path for a text appearance to affect -- the same
     *  reason setSwitchTypeface() just above is a no-op. Kept as a real (if
     *  inert) method so PreferenceCompat / Material callers that configure
     *  android:switchTextAppearance resolve at link time instead of crashing. */
    public void setSwitchTextAppearance(Context context, int resid) {}

    /** AOSP Switch.onCreateDrawableState (android11-release Switch.java:
     *  1445-1451) merges CHECKED_STATE_SET AGAIN, on top of the merge the
     *  CompoundButton superclass already performed -- so a checked Switch's
     *  drawable state carries state_checked TWICE on real Android (the
     *  fwprobe golden reads `checked,checked,enabled,multiline`). StateSet
     *  matching is unaffected by the duplicate; it is mirrored because the
     *  raw array is observable via getDrawableState(). */
    private static final int[] MACAPK_CHECKED_STATE_SET = { 0x010100a0 };

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isChecked()) {
            android.view.View.mergeDrawableStates(drawableState, MACAPK_CHECKED_STATE_SET);
        }
        return drawableState;
    }

    // ==================================================================
    // API-36 surface completion (apidiff: android.widget.Switch had 11 public
    // members missing -- the Icon setters, both tint MODE families, and the
    // styled setSwitchTypeface). AOSP:
    // frameworks/base/core/java/android/widget/Switch.java (android16-release).
    // ==================================================================

    /** AOSP :764 / :583 -- verbatim. */
    public void setThumbIcon(android.graphics.drawable.Icon icon) {
        setThumbDrawable(icon == null ? null : icon.loadDrawable(getContext()));
    }
    public void setTrackIcon(android.graphics.drawable.Icon icon) {
        setTrackDrawable(icon == null ? null : icon.loadDrawable(getContext()));
    }

    // Tint modes. Both families were missing entirely here (neither the
    // PorterDuff.Mode nor the BlendMode form existed), unlike the tint LISTS
    // above. AOSP keeps one BlendMode per tint and converts; we do the same,
    // and additionally keep the PorterDuff.Mode because that is the form this
    // tree's Drawable.setTintMode takes.
    private android.graphics.PorterDuff.Mode mThumbTintMode;
    private android.graphics.PorterDuff.Mode mTrackTintMode;
    private android.graphics.BlendMode mThumbBlendMode;
    private android.graphics.BlendMode mTrackBlendMode;

    public void setThumbTintMode(android.graphics.PorterDuff.Mode tintMode) {
        mThumbTintMode = tintMode;
        mThumbBlendMode = MacApkBlendModes.fromPorterDuff(tintMode);
        if (mThumbDrawable != null) mThumbDrawable.setTintMode(tintMode);
    }
    public android.graphics.PorterDuff.Mode getThumbTintMode() { return mThumbTintMode; }
    public void setThumbTintBlendMode(android.graphics.BlendMode blendMode) {
        mThumbBlendMode = blendMode;
        mThumbTintMode = MacApkBlendModes.toPorterDuff(blendMode);
        if (mThumbDrawable != null) mThumbDrawable.setTintMode(mThumbTintMode);
    }
    public android.graphics.BlendMode getThumbTintBlendMode() { return mThumbBlendMode; }

    public void setTrackTintMode(android.graphics.PorterDuff.Mode tintMode) {
        mTrackTintMode = tintMode;
        mTrackBlendMode = MacApkBlendModes.fromPorterDuff(tintMode);
        if (mTrackDrawable != null) mTrackDrawable.setTintMode(tintMode);
    }
    public android.graphics.PorterDuff.Mode getTrackTintMode() { return mTrackTintMode; }
    public void setTrackTintBlendMode(android.graphics.BlendMode blendMode) {
        mTrackBlendMode = blendMode;
        mTrackTintMode = MacApkBlendModes.toPorterDuff(blendMode);
        if (mTrackDrawable != null) mTrackDrawable.setTintMode(mTrackTintMode);
    }
    public android.graphics.BlendMode getTrackTintBlendMode() { return mTrackBlendMode; }

    /** AOSP :411 setSwitchTypeface(Typeface, int). The FIRST half -- resolving
     *  the requested style onto a concrete Typeface -- is reproduced exactly.
     *  The second half sets fake-bold / skew on Switch's own mTextPaint, the
     *  paint that draws the internal ON/OFF track labels; this port has no
     *  such paint and no Switch.onDraw (see setSwitchTextAppearance above for
     *  the same reason), so there is nothing for those two calls to reach. */
    public void setSwitchTypeface(android.graphics.Typeface tf, int style) {
        if (style > 0) {
            if (tf == null) {
                tf = android.graphics.Typeface.defaultFromStyle(style);
            } else {
                tf = android.graphics.Typeface.create(tf, style);
            }
        }
        setSwitchTypeface(tf);
    }
}
