// android.widget.Space -- a lightweight View used purely to create gaps in a
// layout; it draws nothing and measures to its requested size. Was Absent.
// AOSP: frameworks/base/core/java/android/widget/Space.java
//   public final class Space extends View
package android.widget;

import android.content.Context;
import android.view.View;

public final class Space extends View {
    public Space(Context context) { this(context, null); }
    public Space(Context context, android.util.AttributeSet attrs) {
        this(context, attrs, 0);
    }
    public Space(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // Space never draws; AOSP forces it non-drawing for performance.
        setWillNotDraw(true);
    }
    public Space(Context context, android.util.AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                getDefaultSize2(getSuggestedMinimumWidth(), widthMeasureSpec),
                getDefaultSize2(getSuggestedMinimumHeight(), heightMeasureSpec));
    }

    /** AOSP Space.getDefaultSize2 (android16-release core/java/android/widget/
     *  Space.java:75-92), quoted:
     *
     *      case MeasureSpec.UNSPECIFIED: result = size;                   break;
     *      case MeasureSpec.AT_MOST:     result = Math.min(size, specSize); break;
     *      case MeasureSpec.EXACTLY:     result = specSize;               break;
     *
     *  ⚠ THE AT_MOST ARM USED TO FALL THROUGH INTO EXACTLY here, so a
     *  wrap_content Space returned the WHOLE spec size instead of its own
     *  (usually zero) minimum. Measured against the shipped jar by reflection:
     *  `getDefaultSize2(0, AT_MOST|1652)` answered 1652 where AOSP answers 0.
     *
     *  That is not a spacing nit, it detonates the whole container: the Space
     *  swallows the viewport, its weighted sibling measures 0, LinearLayout's
     *  weight pass then produces a NEGATIVE height, and MEASURED_SIZE_MASK
     *  reinterprets it as ~2^24 -- laying the container's views ~8.4 MILLION px
     *  off-screen. On Telegram that is a completely blank screen 2 with no
     *  exception and the process still running at 55-60fps, i.e. it reads as a
     *  "freeze". The live tree showed `Space meas=808x1652`, sibling
     *  `meas=808x0`, `PhoneView meas=736x16777188` and `text='Your phone
     *  number'` at y=-8388500.
     *
     *  ⚠ DO NOT "simplify" this away by deleting the override and inheriting
     *  View.getDefaultSize. That is precisely the method whose AT_MOST arm DOES
     *  fall through to EXACTLY (View.java, and AOSP's own View.getDefaultSize
     *  reads `case AT_MOST: case EXACTLY: result = specSize;`) -- which is WHY
     *  AOSP carries a private near-copy named getDefaultSize2 in Space at all.
     *  Deleting the override reintroduces exactly the bug fixed here.
     *
     *  Already RULED OUT as the cause by the lane that diagnosed this, so do not
     *  re-derive: ViewGroup.getChildMeasureSpec clamps correctly (:1771),
     *  LinearLayout's weight pass clamps (:650-651), and getMeasuredHeight's
     *  masking is AOSP-faithful (View.java:4244).
     *
     *  Blast radius is fleet-wide and intended: AndroidX AppBar and the Material
     *  spacers both use Space, so this moves pixels on modern apps. It is pure
     *  AOSP parity in the direction of correctness -- a Space that used to eat a
     *  whole axis now occupies its declared size. */
    private static int getDefaultSize2(int size, int measureSpec) {
        int result = size;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (specMode) {
            case MeasureSpec.UNSPECIFIED: result = size;                     break;
            case MeasureSpec.AT_MOST:     result = Math.min(size, specSize); break;
            case MeasureSpec.EXACTLY:     result = specSize;                 break;
        }
        return result;
    }
}
