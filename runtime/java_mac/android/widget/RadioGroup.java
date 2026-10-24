// android.widget.RadioGroup -- a LinearLayout that enforces single
// selection among its RadioButton children. Previously MISSING entirely
// (ClassNotFound when an app inflated <RadioGroup>, and NoSuchMethodError
// on check()/getCheckedRadioButtonId()). Mirrors AOSP's public API +
// the core mutual-exclusion behavior.
package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class RadioGroup extends LinearLayout {
    /** -1 means "nothing checked". */
    private int checkedId = -1;
    private OnCheckedChangeListener onCheckedChangeListener;
    private boolean protectFromCheckedChange;

    // AOSP RadioGroup defaults to VERTICAL, unlike its LinearLayout superclass
    // (android13-release RadioGroup.java):
    //   public RadioGroup(Context context) { super(context); setOrientation(VERTICAL); init(); }
    //   // 2-arg: final int index = attributes.getInt(R.styleable.RadioGroup_orientation, VERTICAL);
    // Ours inherited LinearLayout's HORIZONTAL, so a group whose buttons are
    // added in CODE (no orientation in XML -- UnitConverter's conversion
    // fragment does exactly this) fused its options into one horizontal run of
    // text. An android:orientation in XML still wins: the inflater applies it
    // after construction. See docs/canvas-ab-analyst-report-b_2026-08-02.md item 6.
    public RadioGroup(Context context) { super(context); setOrientation(VERTICAL); }
    public RadioGroup(Context context, AttributeSet attrs) {
        super(context, attrs); setOrientation(VERTICAL);
    }

    /** Listener fired each time the checked RadioButton changes. */
    public interface OnCheckedChangeListener {
        void onCheckedChanged(RadioGroup group, int checkedId);
    }

    // CORRECTED: AOSP overrides the 3-arg TERMINUS, addView(View,int,
    // LayoutParams) -- confirmed against android13-release/core/java/
    // android/widget/RadioGroup.java:154-168, quoted here:
    //   @Override
    //   public void addView(View child, int index, ViewGroup.LayoutParams params) {
    //       if (child instanceof RadioButton) { ... setCheckedId(button.getId()); }
    //       super.addView(child, index, params);
    //   }
    // The previous override here caught only addView(View,int), on the
    // theory that every public addView funnels UP-arity through it. That
    // is true of the 0-arg/2-arg-int/2-arg-width-height overloads, but
    // NOT of addView(View, LayoutParams): per ViewGroup.java:385
    // (`public void addView(View child, LayoutParams lp) { addView(child,
    // -1, lp); }`, matching AOSP ViewGroup.java verbatim) that overload
    // calls the 3-arg terminus DIRECTLY, bypassing addView(View,int)
    // entirely. com.physphil.android.unitconverterultimate's own
    // ConversionFragment.addUnits() populates both RadioGroups via
    // exactly this overload (`mGrpFrom.addView(radioButton,
    // radioGroupLayoutParams)`), with the intended default RadioButton
    // pre-checked before the addView call -- so hookRadioChild() never
    // ran, checkedId stayed -1 forever, and the app's own defensive
    // "no id matched -> getUnits().get(0)" fallback in getCheckedUnit()
    // silently pinned BOTH the "From" and "To" unit pickers to unit
    // index 0. Symptom: converting 1 Sq Kilometre read "1" (an identity
    // Sq-Km-to-Sq-Km conversion) instead of "1000000" (Sq Km to Sq
    // Metre) -- a wrong number on screen with a completely ordinary,
    // correct multiplication behind it. Overriding the 3-arg terminus
    // instead still catches every other addView entry point, since they
    // all funnel here (ViewGroup.java's own "converge UP-arity" note),
    // and matches AOSP exactly instead of approximating it.
    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        hookRadioChild(child);
        super.addView(child, index, params);
    }

    /** When a RadioButton child is added already-checked, adopt it as the
     *  group's selection; and wire its checked-change so a (touch- or
     *  code-) check clears the previously selected sibling. */
    private void hookRadioChild(View child) {
        if (!(child instanceof RadioButton rb)) return;
        if (rb.isChecked()) {
            protectFromCheckedChange = true;
            if (checkedId != -1) setCheckedStateForView(checkedId, false);
            protectFromCheckedChange = false;
            setCheckedId(rb.getId());
        }
        // AOSP hooks the WIDGET slot (RadioGroup.java's mChildOnCheckedChange
        // Listener is installed via CompoundButton.setOnCheckedChangeWidget
        // Listener, not the public setter), precisely so an app that puts its
        // own OnCheckedChangeListener on a radio inside the group does not
        // silently disable mutual exclusion. We used the public slot.
        rb.setOnCheckedChangeWidgetListener((buttonView, isChecked) -> {
            if (protectFromCheckedChange) return;
            if (isChecked) check(buttonView.getId());
        });
    }

    /** Check the RadioButton with the given id (clearing any other). */
    public void check(int id) {
        if (id != -1 && id == checkedId) return;
        protectFromCheckedChange = true;
        if (checkedId != -1) setCheckedStateForView(checkedId, false);
        if (id != -1)        setCheckedStateForView(id, true);
        protectFromCheckedChange = false;
        setCheckedId(id);
    }

    public int getCheckedRadioButtonId() { return checkedId; }

    public void clearCheck() { check(-1); }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.onCheckedChangeListener = listener;
    }

    private void setCheckedId(int id) {
        checkedId = id;
        if (onCheckedChangeListener != null) {
            onCheckedChangeListener.onCheckedChanged(this, id);
        }
    }

    private void setCheckedStateForView(int viewId, boolean checked) {
        View v = findViewById(viewId);
        if (v instanceof CompoundButton cb) cb.setChecked(checked);
    }

    /** RadioGroup.LayoutParams mirrors LinearLayout.LayoutParams; apps
     *  reference the type when reading child margins/weight. */
    /** v1.0.1643: AOSP checkLayoutParams, so addView converts a foreign
     *  LayoutParams into this container's own kind (ViewGroup.addView ->
     *  generateLayoutParams). Gate MACAPK_CHECK_LAYOUT_PARAMS=0 restores the old
     *  accept-anything answer for an A/B. */
    @Override
    protected boolean checkLayoutParams(android.view.ViewGroup.LayoutParams p) {
        if (!MACAPK_CHECK_LP) return true;
        return p instanceof RadioGroup.LayoutParams;
    }
    private static final boolean MACAPK_CHECK_LP =
        !"0".equals(System.getenv("MACAPK_CHECK_LAYOUT_PARAMS"));

    /** AOSP RadioGroup.generateLayoutParams(AttributeSet) / generateDefaultLayoutParams
     *  (android14 :260-276). See TableRow for why this matters since v1.0.1643. */
    @Override
    public android.view.ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new RadioGroup.LayoutParams(getContext(), attrs);
    }
    @Override
    public LinearLayout.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    public static class LayoutParams extends LinearLayout.LayoutParams {
        public LayoutParams(int width, int height) { super(width, height); }
        public LayoutParams(int width, int height, float weight) { super(width, height, weight); }
        /** AOSP setBaseAttributes: WRAP_CONTENT for a size the XML omits. */
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            width = WRAP_CONTENT;
            height = WRAP_CONTENT;
        }
        public LayoutParams(android.view.ViewGroup.LayoutParams p) { super(p); }
        public LayoutParams(android.view.ViewGroup.MarginLayoutParams source) { super(source); }
    }
}
