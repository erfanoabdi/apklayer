// android.widget.AbsoluteLayout -- deprecated ViewGroup that positions each
// child at explicit (x, y) coordinates from its LayoutParams. Was Absent.
// AOSP: frameworks/base/core/java/android/widget/AbsoluteLayout.java
//   @Deprecated public class AbsoluteLayout extends ViewGroup
package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

@Deprecated
public class AbsoluteLayout extends ViewGroup {
    public AbsoluteLayout(Context context) { super(context); }
    public AbsoluteLayout(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public AbsoluteLayout(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public AbsoluteLayout(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();
        int maxHeight = 0, maxWidth = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                measureChild(child, widthMeasureSpec, heightMeasureSpec);
                ViewGroup.LayoutParams lp = child.getLayoutParams();
                int x = (lp instanceof LayoutParams) ? ((LayoutParams) lp).x : 0;
                int y = (lp instanceof LayoutParams) ? ((LayoutParams) lp).y : 0;
                maxWidth = Math.max(maxWidth,
                        x + child.getMeasuredWidth());
                maxHeight = Math.max(maxHeight,
                        y + child.getMeasuredHeight());
            }
        }
        maxWidth += getPaddingLeft() + getPaddingRight();
        maxHeight += getPaddingTop() + getPaddingBottom();
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        setMeasuredDimension(resolveSize(maxWidth, widthMeasureSpec),
                resolveSize(maxHeight, heightMeasureSpec));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                ViewGroup.LayoutParams lp = child.getLayoutParams();
                int x = getPaddingLeft();
                int y = getPaddingTop();
                if (lp instanceof LayoutParams) {
                    x += ((LayoutParams) lp).x;
                    y += ((LayoutParams) lp).y;
                }
                child.layout(x, y, x + child.getMeasuredWidth(),
                        y + child.getMeasuredHeight());
            }
        }
    }

    @Override public ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0);
    }

    /** v1.0.1643: AOSP checkLayoutParams, so addView converts a foreign
     *  LayoutParams into this container's own kind (ViewGroup.addView ->
     *  generateLayoutParams). Gate MACAPK_CHECK_LAYOUT_PARAMS=0 restores the old
     *  accept-anything answer for an A/B. */
    @Override
    protected boolean checkLayoutParams(android.view.ViewGroup.LayoutParams p) {
        if (!MACAPK_CHECK_LP) return true;
        return p instanceof AbsoluteLayout.LayoutParams;
    }
    private static final boolean MACAPK_CHECK_LP =
        !"0".equals(System.getenv("MACAPK_CHECK_LAYOUT_PARAMS"));
    /** AOSP AbsoluteLayout.generateLayoutParams(ViewGroup.LayoutParams). */
    @Override
    protected android.view.ViewGroup.LayoutParams generateLayoutParams(android.view.ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public int x;
        public int y;
        public LayoutParams(int width, int height, int x, int y) {
            super(width, height);
            this.x = x; this.y = y;
        }
        public LayoutParams(Context c, android.util.AttributeSet attrs) { super(c, attrs); }
        public LayoutParams(ViewGroup.LayoutParams source) { super(source); }

        /** AOSP AbsoluteLayout.java:223 -- verbatim, including the
         *  "Absolute.LayoutParams=" prefix AOSP actually emits. */
        public String debug(String output) {
            return output + "Absolute.LayoutParams={width=" + macapkSizeToString(width)
                    + ", height=" + macapkSizeToString(height) + " x=" + x + " y=" + y + "}";
        }

        /** AOSP ViewGroup.LayoutParams.sizeToString(int), inlined -- this
         *  tree's ViewGroup.LayoutParams does not expose it. */
        static String macapkSizeToString(int size) {
            if (size == WRAP_CONTENT) return "wrap-content";
            if (size == MATCH_PARENT) return "match-parent";
            return String.valueOf(size);
        }
    }
}
