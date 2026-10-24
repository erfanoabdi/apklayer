// android.widget.TableRow -- a horizontal LinearLayout row used inside a
// TableLayout; each child occupies a column. Was Absent. We lay out as a
// horizontal LinearLayout (column-span/stretch belong to TableLayout, which
// is a separate partial). AOSP:
//   frameworks/base/core/java/android/widget/TableRow.java
//   public class TableRow extends LinearLayout
package android.widget;

import android.content.Context;
import android.view.View;

public class TableRow extends LinearLayout {
    public interface OnHierarchyChangeListener {}

    public TableRow(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
    }
    public TableRow(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
    }

    public View getVirtualChildAt(int i) { return getChildAt(i); }
    public int  getVirtualChildCount() { return getChildCount(); }

    /** AOSP TableRow.mConstrainedColumnWidths: the column widths TableLayout
     *  computed, handed to every row before it measures (TableLayout
     *  .measureChildBeforeLayout -> setColumnsWidthConstraints). */
    private int[] mConstrainedColumnWidths;

    void setColumnsWidthConstraints(int[] columnWidths) {
        if (columnWidths == null) {
            throw new IllegalArgumentException("columnWidths should be >= 0");
        }
        mConstrainedColumnWidths = columnWidths;
    }

    /** AOSP TableRow.measureChildBeforeLayout (android14): a cell is measured
     *  at EXACTLY(its column's width minus ITS OWN margins) -- the column
     *  width already includes the margins (getColumnsWidths adds them), and
     *  the cell's LayoutParams are never modified. v1.0.1643: TableLayout used
     *  to reach the same size by WRITING lp.width = columnWidth, which folded
     *  the margins into lp.width, so every re-measure grew the column by the
     *  cell's margins (0, 20, 40, ...). AOSP's horizontal-gravity (AT_MOST +
     *  offset) branch is not modelled; such a cell is measured EXACTLY. */
    @Override
    void measureChildBeforeLayout(View child, int childIndex, int widthMeasureSpec,
            int totalWidth, int heightMeasureSpec, int totalHeight) {
        final int[] constrained = mConstrainedColumnWidths;
        if (constrained != null && childIndex < constrained.length) {
            final android.view.ViewGroup.LayoutParams raw = child.getLayoutParams();
            final int span = (raw instanceof LayoutParams trlp) ? Math.max(1, trlp.span) : 1;
            int columnWidth = 0;
            for (int i = 0; i < span && childIndex + i < constrained.length; i++) {
                columnWidth += constrained[childIndex + i];
            }
            final int lm = (raw instanceof android.view.ViewGroup.MarginLayoutParams m) ? m.leftMargin : 0;
            final int rm = (raw instanceof android.view.ViewGroup.MarginLayoutParams m) ? m.rightMargin : 0;
            final int tm = (raw instanceof android.view.ViewGroup.MarginLayoutParams m) ? m.topMargin : 0;
            final int bm = (raw instanceof android.view.ViewGroup.MarginLayoutParams m) ? m.bottomMargin : 0;
            final int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                    Math.max(0, columnWidth - lm - rm), MeasureSpec.EXACTLY);
            final int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                    getPaddingTop() + getPaddingBottom() + tm + bm + totalHeight,
                    raw != null ? raw.height : android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        } else {
            // AOSP: "fail silently when column widths are not available"
            super.measureChildBeforeLayout(child, childIndex, widthMeasureSpec,
                    totalWidth, heightMeasureSpec, totalHeight);
        }
    }

    /** v1.0.1643: AOSP checkLayoutParams, so addView converts a foreign
     *  LayoutParams into this container's own kind (ViewGroup.addView ->
     *  generateLayoutParams). Gate MACAPK_CHECK_LAYOUT_PARAMS=0 restores the old
     *  accept-anything answer for an A/B. */
    @Override
    protected boolean checkLayoutParams(android.view.ViewGroup.LayoutParams p) {
        if (!MACAPK_CHECK_LP) return true;
        return p instanceof TableRow.LayoutParams;
    }
    private static final boolean MACAPK_CHECK_LP =
        !"0".equals(System.getenv("MACAPK_CHECK_LAYOUT_PARAMS"));
    /** AOSP TableRow.generateLayoutParams(ViewGroup.LayoutParams). */
    @Override
    protected LinearLayout.LayoutParams generateLayoutParams(android.view.ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    /** AOSP TableRow.generateLayoutParams(AttributeSet) / generateDefaultLayoutParams
     *  (android14 :360-366): inflated cells carry TableRow.LayoutParams, so the
     *  v1.0.1643 checkLayoutParams never converts them (a conversion through
     *  the ViewGroup.LayoutParams copy constructor drops weight and margins). */
    @Override
    public android.view.ViewGroup.LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
        return new TableRow.LayoutParams(getContext(), attrs);
    }
    @Override
    public LinearLayout.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    public static class LayoutParams extends LinearLayout.LayoutParams {
        public int column = -1;
        public int span = 1;

        public LayoutParams(int w, int h) { super(w, h); }
        public LayoutParams(int w, int h, float initWeight) { super(w, h, initWeight); }
        public LayoutParams() { super(MATCH_PARENT, WRAP_CONTENT); }
        public LayoutParams(int column) { super(MATCH_PARENT, WRAP_CONTENT); this.column = column; }
        /** AOSP setBaseAttributes: a cell that declares no size is MATCH_PARENT
         *  x WRAP_CONTENT (the inflater then applies whatever the XML does say). */
        public LayoutParams(Context c, android.util.AttributeSet attrs) {
            super(c, attrs);
            width = MATCH_PARENT;
            height = WRAP_CONTENT;
        }
        public LayoutParams(android.view.ViewGroup.LayoutParams p) { super(p); }
        public LayoutParams(android.view.ViewGroup.MarginLayoutParams source) { super(source); }
    }
}
