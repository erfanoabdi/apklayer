// android.widget.RadioButton -- a two-state button that, unlike a
// CheckBox, cannot be unchecked by the user (only by checking another
// button in its RadioGroup). Previously an empty stub (any method call
// -> NoSuchMethodError; inflation fell back to a blank FrameLayout).
// Mirrors AOSP: RadioButton extends CompoundButton (exactly like the
// working CheckBox / Switch siblings).
package android.widget;

import android.content.Context;
import android.util.AttributeSet;

public class RadioButton extends CompoundButton {
    // AOSP RadioButton (android16-release core/java/android/widget/RadioButton.java:
    // 56-70) chains (Context)/(Context,AttributeSet) down to
    // this(context, attrs, com.android.internal.R.attr.radioButtonStyle) --
    // 0x0101007e, verified against public-final.xml. Same gap as CheckBox:
    // forwarding straight to CompoundButton with defStyleAttr=0 left a
    // programmatic `new RadioButton(ctx)` with no default style; XML
    // inflation already gets its indicator via LayoutInflater's tier 3.
    private static final int RADIOBUTTON_STYLE = 0x0101007e;
    public RadioButton(Context context) { this(context, null); }
    public RadioButton(Context context, AttributeSet attrs) { this(context, attrs, RADIOBUTTON_STYLE); }
    public RadioButton(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }
    public RadioButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) { super(context, attrs, defStyleAttr); }

    // AOSP: a click on an already-checked RadioButton is a no-op (it
    // does NOT toggle off); only checking a sibling clears it.
    @Override
    public void toggle() {
        if (!isChecked()) setChecked(true);
    }
}
