// android.widget.TwoLineListItem -- deprecated RelativeLayout with a primary
// (text1) and secondary (text2) TextView. Was Absent. We expose getText1/
// getText2 by looking up the standard android.R.id ids. AOSP:
//   frameworks/base/core/java/android/widget/TwoLineListItem.java
//   @Deprecated public class TwoLineListItem extends RelativeLayout
package android.widget;

import android.content.Context;
import android.view.View;

@Deprecated
public class TwoLineListItem extends RelativeLayout {
    private TextView mText1;
    private TextView mText2;

    public TwoLineListItem(Context context) { super(context); }
    public TwoLineListItem(Context context, android.util.AttributeSet attrs) { super(context, attrs); }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public TwoLineListItem(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public TwoLineListItem(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override public void onFinishInflate() {
        try { super.onFinishInflate(); } catch (Throwable ignored) {}
        // The ids MUST be the frozen AOSP public ones. Verified 2026-07-29
        // against core/res/res/values/public-final.xml:
        //   <public type="id" name="text1" id="0x01020014" />
        //   <public type="id" name="text2" id="0x01020015" />
        // These were previously 0x0102000e / 0x0102000f, which are
        //   <public type="id" name="selectedIcon"      id="0x0102000e" />
        //   <public type="id" name="secondaryProgress" id="0x0102000f" />
        // i.e. two completely unrelated platform ids, so getText1()/getText2()
        // could only ever return null. public.xml ids are immutable by
        // definition (they are the platform's resource ABI), so there is no
        // API-level under which the old numbers were right.
        View v1 = findViewById(0x01020014); // android.R.id.text1
        View v2 = findViewById(0x01020015); // android.R.id.text2
        if (v1 instanceof TextView) mText1 = (TextView) v1;
        if (v2 instanceof TextView) mText2 = (TextView) v2;
    }

    public TextView getText1() { return mText1; }
    public TextView getText2() { return mText2; }
}
