// android.widget.CheckBox -- two-state checkbox widget. Subclass of
// CompoundButton. androidx.preference.CheckBoxPreference references
// this as the layout widget.
package android.widget;

import android.content.Context;
import android.util.AttributeSet;

public class CheckBox extends CompoundButton {
    // AOSP CheckBox (android16-release core/java/android/widget/CheckBox.java:58-72)
    // chains the (Context) and (Context,AttributeSet) ctors down to
    // this(context, attrs, com.android.internal.R.attr.checkboxStyle) --
    // 0x0101006c, verified against public-final.xml. We used to forward each
    // overload straight to CompoundButton with the SAME args, which for the
    // 1-arg and 2-arg forms meant defStyleAttr=0 (CompoundButton's own
    // fallback): a programmatic `new CheckBox(ctx)` got no default style at
    // all. XML-inflated CheckBoxes were unaffected (LayoutInflater's tier 3
    // already special-cases CheckBox -> checkboxStyle for the indicator
    // drawable), so this only mattered for code-built checkboxes.
    private static final int CHECKBOX_STYLE = 0x0101006c;
    public CheckBox(Context context) { this(context, null); }
    public CheckBox(Context context, AttributeSet attrs) { this(context, attrs, CHECKBOX_STYLE); }
    public CheckBox(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }
    public CheckBox(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) { super(context, attrs, defStyleAttr); }
}
