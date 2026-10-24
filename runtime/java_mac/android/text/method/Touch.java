package android.text.method;

import android.text.Layout;
import android.text.Spannable;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

/**
 * Drag-scroll support for TextViews backed by a Spannable.
 *
 * MovementMethod implementations (ScrollingMovementMethod,
 * LinkMovementMethod, ArrowKeyMovementMethod) all delegate their touch
 * handling here, so a missing Touch means those movement methods cannot
 * compile against the framework at all.
 *
 * Source: frameworks/base/core/java/android/text/method/Touch.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/text/method/Touch.java
 */
public class Touch {

    private Touch() { }

    /**
     * Scroll so that the character at the given offset is visible, using the
     * same left-edge rule AOSP uses (line direction decides which edge).
     */
    public static void scrollTo(TextView widget, Layout layout, int x, int y) {
        if (widget == null || layout == null) return;
        final int horizontalPadding = widget.getTotalPaddingLeft() + widget.getTotalPaddingRight();
        final int availableWidth = widget.getWidth() - horizontalPadding;

        final int top = layout.getLineForVertical(y);
        final int bottom = layout.getLineForVertical(y + widget.getHeight()
                - widget.getTotalPaddingTop() - widget.getTotalPaddingBottom());

        int left = Integer.MAX_VALUE;
        int right = 0;
        for (int i = top; i <= bottom; i++) {
            left = (int) Math.min(left, Math.floor(layout.getLineLeft(i)));
            right = (int) Math.max(right, Math.ceil(layout.getLineRight(i)));
        }

        final int actualWidth = right - left;
        if (actualWidth < availableWidth) {
            // Content narrower than the viewport: pin to the natural edge.
            left = right - availableWidth;
        }
        x = Math.max(left, Math.min(right - availableWidth, x));
        y = Math.max(0, Math.min(layout.getHeight() - (widget.getHeight()
                - widget.getTotalPaddingTop() - widget.getTotalPaddingBottom()), y));
        widget.scrollTo(x, y);
    }

    /** Handle a drag; returns true once the gesture has become a scroll. */
    public static boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
        if (widget == null || buffer == null || event == null) return false;
        DragState[] ds;

        switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
            ds = buffer.getSpans(0, buffer.length(), DragState.class);
            for (int i = 0; i < ds.length; i++) buffer.removeSpan(ds[i]);
            buffer.setSpan(new DragState(event.getX(), event.getY(),
                    widget.getScrollX(), widget.getScrollY()),
                    0, 0, Spannable.SPAN_MARK_MARK);
            return true;

        case MotionEvent.ACTION_UP:
            ds = buffer.getSpans(0, buffer.length(), DragState.class);
            for (int i = 0; i < ds.length; i++) buffer.removeSpan(ds[i]);
            return ds.length > 0 && ds[0].mUsed;

        case MotionEvent.ACTION_MOVE:
            ds = buffer.getSpans(0, buffer.length(), DragState.class);
            if (ds.length > 0) {
                if (!ds[0].mFarEnough) {
                    int slop = ViewConfiguration.get(widget.getContext()).getScaledTouchSlop();
                    if (Math.abs(event.getX() - ds[0].mX) >= slop
                            || Math.abs(event.getY() - ds[0].mY) >= slop) {
                        ds[0].mFarEnough = true;
                    }
                }
                if (ds[0].mFarEnough) {
                    ds[0].mUsed = true;
                    float dx = ds[0].mX - event.getX();
                    float dy = ds[0].mY - event.getY();
                    int nx = ds[0].mScrollX + (int) dx;
                    int ny = ds[0].mScrollY + (int) dy;
                    Layout layout = widget.getLayout();
                    if (layout != null) scrollTo(widget, layout, nx, ny);
                    widget.cancelLongPress();
                    return true;
                }
            }
            return false;
        default:
            return false;
        }
    }

    /** Horizontal drag distance so far; AOSP exposes this for text selection. */
    public static int getInitialScrollX(TextView widget, Spannable buffer) {
        DragState[] ds = buffer.getSpans(0, buffer.length(), DragState.class);
        return ds.length > 0 ? ds[0].mScrollX : -1;
    }

    public static int getInitialScrollY(TextView widget, Spannable buffer) {
        DragState[] ds = buffer.getSpans(0, buffer.length(), DragState.class);
        return ds.length > 0 ? ds[0].mScrollY : -1;
    }

    /** Per-gesture state, parked in the Spannable as a zero-length span. */
    private static class DragState implements android.text.NoCopySpan {
        public float mX, mY;
        public int mScrollX, mScrollY;
        public boolean mFarEnough, mUsed;

        public DragState(float x, float y, int scrollX, int scrollY) {
            mX = x; mY = y; mScrollX = scrollX; mScrollY = scrollY;
        }
    }
}
