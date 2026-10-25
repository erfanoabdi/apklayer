// android.widget.GridView -- AbsListView that lays its rows out in a grid of
// N columns. Was a bare `extends Object` stub (broke instanceof AbsListView /
// AdapterView and any grid inflate). Now a real AbsListView subclass with a
// column-aware fill: it measures the column width from numColumns (or from a
// requested columnWidth in AUTO_FIT mode), then lays adapter items left-to-
// right, top-to-bottom, reusing AbsListView.obtainView for row recycling.
// Vertical drag-scroll is inherited from AbsListView. AOSP:
//   frameworks/base/core/java/android/widget/GridView.java
//   public class GridView extends AbsListView
package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

public class GridView extends AbsListView {
    public static final int AUTO_FIT  = -1;
    public static final int NO_STRETCH            = 0;
    public static final int STRETCH_SPACING       = 1;
    public static final int STRETCH_COLUMN_WIDTH  = 2;
    public static final int STRETCH_SPACING_UNIFORM = 3;

    private int mNumColumns = AUTO_FIT;
    private int mRequestedColumnWidth = 0;
    private int mRequestedHorizontalSpacing = 0;
    private int mVerticalSpacing = 0;
    private int mStretchMode = STRETCH_COLUMN_WIDTH;
    private int mColumnWidth;
    private int mGravity = 0;

    public GridView(Context c) { super(c); }
    public GridView(Context c, android.util.AttributeSet a) { super(c, a); }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public GridView(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public GridView(Context c, android.util.AttributeSet a, int s) { super(c, a, s); }

    public void setNumColumns(int numColumns) { mNumColumns = numColumns; requestLayout(); }
    public int  getNumColumns() {
        return mNumColumns == AUTO_FIT ? Math.max(1, computeAutoColumns()) : mNumColumns;
    }
    public void setColumnWidth(int columnWidth) { mRequestedColumnWidth = columnWidth; requestLayout(); }
    public int  getColumnWidth() { return mColumnWidth; }
    public int  getRequestedColumnWidth() { return mRequestedColumnWidth; }
    public void setHorizontalSpacing(int hs) { mRequestedHorizontalSpacing = hs; requestLayout(); }
    public int  getHorizontalSpacing() { return mRequestedHorizontalSpacing; }
    public int  getRequestedHorizontalSpacing() { return mRequestedHorizontalSpacing; }
    public void setVerticalSpacing(int vs) { mVerticalSpacing = vs; requestLayout(); }
    public int  getVerticalSpacing() { return mVerticalSpacing; }
    public void setStretchMode(int stretchMode) { mStretchMode = stretchMode; requestLayout(); }
    public int  getStretchMode() { return mStretchMode; }
    public void setGravity(int gravity) { mGravity = gravity; requestLayout(); }
    public int  getGravity() { return mGravity; }

    private int computeAutoColumns() {
        int avail = getWidth() - getPaddingLeft() - getPaddingRight();
        if (mRequestedColumnWidth > 0 && avail > 0) {
            return Math.max(1, (avail + mRequestedHorizontalSpacing)
                    / (mRequestedColumnWidth + mRequestedHorizontalSpacing));
        }
        return 2; // AOSP defaults numColumns to 2 (AUTO_FIT with no width hint)
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        layoutGrid();
    }

    private void layoutGrid() {
        if (getAdapter() == null) {
            if (AbsListView.ITEMCLICK_AOSP) removeAllViewsInLayout();  // AOSP resetList()
            macapkGridLayoutDone();
            return;
        }
        removeAllViewsInLayout();
        final int count = getAdapter().getCount();
        if (count == 0) { macapkGridLayoutDone(); return; }

        final int cols = Math.max(1, getNumColumns());
        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();
        final int avail = getWidth() - paddingLeft - getPaddingRight();
        final int hs = mRequestedHorizontalSpacing;
        mColumnWidth = mRequestedColumnWidth > 0 && mStretchMode == NO_STRETCH
                ? mRequestedColumnWidth
                : Math.max(1, (avail - hs * (cols - 1)) / cols);

        final int bottomEdge = getHeight() - getPaddingBottom();
        int pos = Math.max(0, mFirstPosition);
        int rowTop = paddingTop;

        while (pos < count && rowTop < bottomEdge) {
            int rowHeight = 0;
            for (int c = 0; c < cols && pos < count; c++, pos++) {
                View child = obtainView(pos);
                int childWidthSpec = MeasureSpec.makeMeasureSpec(
                        mColumnWidth, MeasureSpec.EXACTLY);
                int childHeightSpec = MeasureSpec.makeMeasureSpec(
                        0, MeasureSpec.UNSPECIFIED);
                child.measure(childWidthSpec, childHeightSpec);
                int h = child.getMeasuredHeight();
                int x = paddingLeft + c * (mColumnWidth + hs);
                addViewInLayout(child, getChildCount(), child.getLayoutParams());
                child.layout(x, rowTop, x + mColumnWidth, rowTop + h);
                bindGridClick(child, pos);
                if (h > rowHeight) rowHeight = h;
            }
            rowTop += rowHeight + mVerticalSpacing;
        }
        macapkGridLayoutDone();
    }

    private void bindGridClick(final View cell, final int position) {
        if (AbsListView.ITEMCLICK_AOSP) return;  // AbsListView.onTouchEvent clicks cells, as in AOSP
        final long id = getAdapter().getItemId(position);
        cell.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { performItemClick(cell, position, id); }
        });
    }

    /** AOSP GridView :882 -- a public override of AbsListView's
     *  package-private smoothScrollByOffset(int), same as ListView's. */
    @Override
    public void smoothScrollByOffset(int offset) { super.smoothScrollByOffset(offset); }
}
