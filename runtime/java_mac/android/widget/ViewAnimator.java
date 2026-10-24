// android.widget.ViewAnimator -- a FrameLayout that shows ONE of its
// children at a time, animating between them. Base of ViewFlipper /
// ViewSwitcher / TextSwitcher / ImageSwitcher. Was Absent. We honor the
// displayed-child model and child management; the in/out Animation objects
// are stored but we switch instantly (no per-frame tween pump). AOSP:
//   frameworks/base/core/java/android/widget/ViewAnimator.java
//   public class ViewAnimator extends FrameLayout
package android.widget;

import android.content.Context;
import android.view.View;
import android.view.animation.Animation;

public class ViewAnimator extends FrameLayout {
    private int mWhichChild = 0;
    private boolean mFirstTime = true;
    private boolean mAnimateFirstTime = true;
    private Animation mInAnimation;
    private Animation mOutAnimation;

    public ViewAnimator(Context context) { super(context); }
    public ViewAnimator(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public void setDisplayedChild(int whichChild) {
        mWhichChild = whichChild;
        if (whichChild >= getChildCount()) mWhichChild = 0;
        else if (whichChild < 0) mWhichChild = getChildCount() - 1;
        boolean hasFocus = getFocusedChild() != null;
        showOnly(mWhichChild);
        if (hasFocus) requestFocus(FOCUS_FORWARD);
    }
    public int getDisplayedChild() { return mWhichChild; }

    public void showNext() { setDisplayedChild(mWhichChild + 1); }
    public void showPrevious() { setDisplayedChild(mWhichChild - 1); }

    void showOnly(int childIndex) {
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            child.setVisibility(i == childIndex ? View.VISIBLE : View.GONE);
        }
        mFirstTime = false;
    }

    @Override public void addView(View child, int index,
            android.view.ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        if (getChildCount() == 1) child.setVisibility(View.VISIBLE);
        else child.setVisibility(View.GONE);
        if (index >= 0 && mWhichChild >= index) mWhichChild++;
    }

    @Override public void removeAllViews() {
        super.removeAllViews();
        mWhichChild = 0;
        mFirstTime = true;
    }
    @Override public void removeView(View view) {
        int index = indexOfChild(view);
        if (index >= 0) removeViewAt(index);
    }
    @Override public void removeViewAt(int index) {
        super.removeViewAt(index);
        final int childCount = getChildCount();
        if (childCount == 0) mWhichChild = 0;
        else if (mWhichChild >= childCount) setDisplayedChild(childCount - 1);
        else if (mWhichChild == index) setDisplayedChild(mWhichChild);
    }

    public View getCurrentView() { return getChildAt(mWhichChild); }

    public Animation getInAnimation() { return mInAnimation; }
    public void setInAnimation(Animation inAnimation) { mInAnimation = inAnimation; }
    public Animation getOutAnimation() { return mOutAnimation; }
    public void setOutAnimation(Animation outAnimation) { mOutAnimation = outAnimation; }
    public void setInAnimation(Context context, int resourceID) {}
    public void setOutAnimation(Context context, int resourceID) {}

    public boolean getAnimateFirstView() { return mAnimateFirstTime; }
    public void setAnimateFirstView(boolean animate) { mAnimateFirstTime = animate; }
}
