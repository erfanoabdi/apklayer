// android.widget.ToggleButton -- a CompoundButton that displays a
// distinct label for its on / off state ("ON"/"OFF" by default).
// Previously an empty stub (NoSuchMethodError on any call; inflation
// fell back to a blank FrameLayout). Mirrors AOSP: extends
// CompoundButton, swaps its text on checked-state change.
package android.widget;

import android.content.Context;
import android.util.AttributeSet;

public class ToggleButton extends CompoundButton {
    private CharSequence textOn  = "ON";
    private CharSequence textOff = "OFF";

    // AOSP ToggleButton (android16-release core/java/android/widget/
    // ToggleButton.java:67-77) chains (Context)/(Context,AttributeSet) down
    // to this(context, attrs, com.android.internal.R.attr.buttonStyleToggle)
    // -- 0x0101004b, verified against public-final.xml. Same CompoundButton
    // gap as the other three siblings: a programmatic `new ToggleButton(ctx)`
    // used to get defStyleAttr=0, i.e. no default style at all.
    // syncText() is called only from the two constructors below that actually
    // reach `super(...)` -- the (Context) and (Context,AttributeSet) forms now
    // chain THROUGH this(...) (per AOSP), so calling it there too would just
    // run syncText() twice for every construction path.
    private static final int BUTTONSTYLETOGGLE = 0x0101004b;
    public ToggleButton(Context context) { this(context, null); }
    public ToggleButton(Context context, AttributeSet attrs) { this(context, attrs, BUTTONSTYLETOGGLE); }
    public ToggleButton(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); restoreButtonGravity(); syncText(); }
    public ToggleButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) { super(context, attrs, defStyleAttr); restoreButtonGravity(); syncText(); }

    /** ToggleButton is the ONE CompoundButton subclass whose default style does
     *  not descend from `Widget.CompoundButton`: buttonStyleToggle resolves to
     *  `Widget.Button.Toggle`, whose parent is `Widget.Button` (android16-release
     *  core/res/res/values/styles.xml:402-407 and :359-366), so it keeps
     *  `gravity=center_vertical|center_horizontal` -- a toggle's ON/OFF label is
     *  centred like any button's, not start-aligned beside an indicator.
     *  CompoundButton's constructor applies `Widget.CompoundButton`'s
     *  center_vertical|start for the checkbox/radio/switch majority; undo it
     *  here rather than leaving the whole family wrong for one member. An
     *  android:gravity in XML still wins (the inflater runs after construction). */
    private void restoreButtonGravity() {
        setGravity(android.view.Gravity.CENTER);
    }

    public CharSequence getTextOn()  { return textOn; }
    public CharSequence getTextOff() { return textOff; }
    public void setTextOn(CharSequence textOn)   { this.textOn = textOn; syncText(); }
    public void setTextOff(CharSequence textOff) { this.textOff = textOff; syncText(); }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        syncText();
    }

    /** Show textOn when checked, textOff otherwise (AOSP syncTextState). */
    private void syncText() {
        setText(isChecked() ? textOn : textOff);
    }

    // ---- API-36 surface completion ------------------------------------
    /** AOSP ToggleButton.getDisabledAlpha() (:146) -- the alpha applied to the
     *  ON/OFF indicator strip when the button is disabled, read from
     *  R.styleable.ToggleButton_disabledAlpha with a 0.5f default (:59). We do
     *  not paint the indicator strip, so nothing consumes the value, but the
     *  DEFAULT is part of the observable contract and 0.5f is what a device
     *  answers on a ToggleButton whose style does not override it. */
    private float mDisabledAlpha = 0.5f;
    public float getDisabledAlpha() { return mDisabledAlpha; }
}
