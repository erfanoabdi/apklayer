// android.widget.StackView -- an AdapterViewAnimator that shows items as a
// stack of cards. Was Absent. We reuse AdapterViewAnimator's single-item
// model; the stacked-card 3D chrome is not painted. AOSP:
//   frameworks/base/core/java/android/widget/StackView.java
//   public class StackView extends AdapterViewAnimator
package android.widget;

import android.content.Context;

public class StackView extends AdapterViewAnimator {
    public StackView(Context context) { super(context); }
    public StackView(Context context, android.util.AttributeSet attrs) { super(context, attrs); }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public StackView(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public StackView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
}
