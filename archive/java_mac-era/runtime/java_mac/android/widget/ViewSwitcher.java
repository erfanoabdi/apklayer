// android.widget.ViewSwitcher -- a ViewAnimator limited to exactly two
// children that flips between them; a ViewFactory can lazily create the two
// children. Base of TextSwitcher / ImageSwitcher. Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/ViewSwitcher.java
//   public class ViewSwitcher extends ViewAnimator
package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

public class ViewSwitcher extends ViewAnimator {
    ViewFactory mFactory;

    public ViewSwitcher(Context context) { super(context); }
    public ViewSwitcher(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    @Override public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (getChildCount() >= 2) {
            throw new IllegalStateException(
                    "Can't add more than 2 views to a ViewSwitcher");
        }
        super.addView(child, index, params);
    }

    public View getNextView() {
        int which = getDisplayedChild() == 0 ? 1 : 0;
        return getChildAt(which);
    }

    private View obtainView() {
        View child = mFactory.makeView();
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        addView(child, lp);
        return child;
    }

    public void setFactory(ViewFactory factory) {
        mFactory = factory;
        obtainView();
        obtainView();
    }

    public void reset() {
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setVisibility(View.GONE);
        }
    }

    public interface ViewFactory {
        View makeView();
    }
}
