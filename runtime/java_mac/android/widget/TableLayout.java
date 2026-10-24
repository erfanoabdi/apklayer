// android.widget.TableLayout -- LinearLayout subclass that arranges its
// children (typically TableRow) into rows and synchronizes column widths
// across all rows, mirroring AOSP's actual raison-d'etre for this class
// over a plain vertical LinearLayout of independent rows.
//
// AOSP ref: frameworks/base/core/java/android/widget/TableLayout.java.
// TableLayout overrides onMeasure to run findLargestCells (measure every
// row's cells once, unspanned, tracking the widest cell seen so far per
// column index into mMaxWidths) then shrinkAndStretchColumns (redistribute
// the difference between the summed column widths and the available width
// across columns flagged stretchable/shrinkable, or all columns if
// setStretch/ShrinkAllColumns was used), then forces every row's matching
// cell to that column's final width before the rows are actually measured.
//
// AOSP reaches the "force cell N to column N's width" step through a
// TableRow-internal mConstrainedColumnWidths field consulted from
// TableRow's package-private measureChildBeforeLayout hook (called per
// child by LinearLayout's measureHorizontal). Since v1.0.1643 we do the same:
// applyColumnWidths() hands each row mMaxWidths and TableRow measures each
// cell at EXACTLY(column - its margins). (Before, this file WROTE every cell's
// LayoutParams.width, which folded margins into it and grew the column on
// every re-measure.)
//
// Known, disclosed simplifications versus AOSP (neither is this fix's
// target; per the project's "no per-APK code / do the real work" rules on
// scoping a single fix, these are left as honest gaps rather than guessed
// at):
//   - layout_span > 1 cells are excluded from the column-width computation
//     (contribute 0, same as an AOSP spanned cell would), but are NOT
//     summed back across their spanned columns at apply time, because that
//     needs TableRow's AOSP virtual-column remapping
//     (mapIndexAndColumns / getVirtualChildAt returning a DEFLECTED child
//     index for a spanned row), which our TableRow does not implement --
//     getVirtualChildAt there is a trivial getChildAt passthrough.
//   - stretchColumns/shrinkColumns/collapseColumns XML attributes are not
//     parsed (no R.styleable.TableLayout_* wiring in the AttributeSet
//     constructor); use setColumnStretchable/setColumnShrinkable/
//     setColumnCollapsed programmatically instead.
//     setShrinkAllColumns/setStretchAllColumns work either way.
package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class TableLayout extends LinearLayout {
    /** Per-column max cell width from the last measure pass (AOSP
     *  mMaxWidths). Grows to fit the widest row seen so far; never shrinks
     *  (AOSP: "the array can grow but never shrinks. Unused extra cells in
     *  the array are just ignored"). */
    private int[] mMaxWidths;

    private final java.util.TreeMap<Integer, Boolean> mStretchableColumns = new java.util.TreeMap<>();
    private final java.util.TreeMap<Integer, Boolean> mShrinkableColumns  = new java.util.TreeMap<>();
    private final java.util.TreeMap<Integer, Boolean> mCollapsedColumns  = new java.util.TreeMap<>();

    private boolean mShrinkAllColumns;
    private boolean mStretchAllColumns;

    public TableLayout(Context c) {
        super(c);
        setOrientation(LinearLayout.VERTICAL);
    }
    public TableLayout(Context c, AttributeSet a) {
        super(c, a);
        setOrientation(LinearLayout.VERTICAL);
    }

    public void setColumnStretchable(int columnIndex, boolean isStretchable) {
        mStretchableColumns.put(columnIndex, isStretchable);
        requestLayout();
    }
    public void setColumnShrinkable(int columnIndex, boolean isShrinkable) {
        mShrinkableColumns.put(columnIndex, isShrinkable);
        requestLayout();
    }
    /** AOSP: collapses (GONE) or restores (VISIBLE) column columnIndex in
     *  every current row. AOSP dispatches to a package-private
     *  TableRow.setColumnCollapsed on each row; ours has no such hook, so
     *  reach the same cell directly through the public getVirtualChildAt
     *  surface TableRow does expose -- same observable effect. Rows added
     *  AFTER this call do not automatically inherit the collapsed state
     *  (AOSP's PassThroughHierarchyChangeListener.trackCollapsedColumns
     *  does; wiring an addView-time hook for that is a separate, smaller
     *  gap left for a future pass). */
    public void setColumnCollapsed(int columnIndex, boolean isCollapsed) {
        mCollapsedColumns.put(columnIndex, isCollapsed);
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View view = getChildAt(i);
            if (view instanceof TableRow row) {
                final View cell = row.getVirtualChildAt(columnIndex);
                if (cell != null) {
                    cell.setVisibility(isCollapsed ? View.GONE : View.VISIBLE);
                }
            }
        }
        requestLayout();
    }
    public boolean isColumnStretchable(int columnIndex) {
        final Boolean v = mStretchableColumns.get(columnIndex);
        return mStretchAllColumns || (v != null && v);
    }
    public boolean isColumnShrinkable(int columnIndex) {
        final Boolean v = mShrinkableColumns.get(columnIndex);
        return mShrinkAllColumns || (v != null && v);
    }
    public boolean isColumnCollapsed(int columnIndex) {
        final Boolean v = mCollapsedColumns.get(columnIndex);
        return v != null && v;
    }
    public void setShrinkAllColumns(boolean shrink) { mShrinkAllColumns = shrink; requestLayout(); }
    public boolean isShrinkAllColumns() { return mShrinkAllColumns; }
    public void setStretchAllColumns(boolean stretch) { mStretchAllColumns = stretch; requestLayout(); }
    public boolean isStretchAllColumns() { return mStretchAllColumns; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        findLargestCells(widthMeasureSpec, heightMeasureSpec);
        shrinkAndStretchColumns(widthMeasureSpec);
        applyColumnWidths();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** AOSP TableLayout.findLargestCells: measure every row's unspanned
     *  cells and track the max width seen so far at each column index. */
    private void findLargestCells(int widthMeasureSpec, int heightMeasureSpec) {
        boolean firstRow = true;
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child == null || child.getVisibility() == View.GONE) continue;
            if (!(child instanceof TableRow row)) continue;

            // AOSP: "the layout_height attribute can be defined by a
            // child... If the child is a TableRow, then the height is
            // always WRAP_CONTENT".
            final ViewGroup.LayoutParams rowParams = row.getLayoutParams();
            if (rowParams != null) rowParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;

            final int[] widths = measureRowCells(row, widthMeasureSpec, heightMeasureSpec);
            final int newLength = widths.length;
            if (firstRow) {
                if (mMaxWidths == null || mMaxWidths.length != newLength) {
                    mMaxWidths = new int[newLength];
                }
                System.arraycopy(widths, 0, mMaxWidths, 0, newLength);
                firstRow = false;
            } else {
                int length = mMaxWidths.length;
                final int difference = newLength - length;
                if (difference > 0) {
                    final int[] oldMaxWidths = mMaxWidths;
                    mMaxWidths = new int[newLength];
                    System.arraycopy(oldMaxWidths, 0, mMaxWidths, 0, oldMaxWidths.length);
                    System.arraycopy(widths, oldMaxWidths.length, mMaxWidths, oldMaxWidths.length, difference);
                }
                final int[] maxWidths = mMaxWidths;
                length = Math.min(length, newLength);
                for (int j = 0; j < length; j++) {
                    maxWidths[j] = Math.max(maxWidths[j], widths[j]);
                }
            }
        }
    }

    /** Mirrors AOSP TableRow.getColumnsWidths(int,int), reimplemented
     *  against the public getVirtualChildAt/getVirtualChildCount surface
     *  TableRow exposes (the real method is package-private and our
     *  TableRow doesn't have it). A cell's LayoutParams is normally a
     *  TableRow.LayoutParams (column + span), but a cell added via
     *  addView(view) with no explicit LayoutParams falls back to whatever
     *  LinearLayout.generateDefaultLayoutParams() produces instead
     *  (TableRow doesn't override that), so a non-TableRow.LayoutParams
     *  cell is treated as span=1 (the common, unspanned case) rather than
     *  excluded outright. */
    private int[] measureRowCells(TableRow row, int widthMeasureSpec, int heightMeasureSpec) {
        final int numColumns = row.getVirtualChildCount();
        final int[] widths = new int[numColumns];
        for (int i = 0; i < numColumns; i++) {
            final View child = row.getVirtualChildAt(i);
            if (child == null || child.getVisibility() == View.GONE) continue;   // widths[i] stays 0

            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp == null) continue;
            final int span = (lp instanceof TableRow.LayoutParams trlp) ? trlp.span : 1;
            if (span != 1) continue;   // spanned cells don't force a single column wider (AOSP)

            final int spec;
            switch (lp.width) {
                case ViewGroup.LayoutParams.WRAP_CONTENT:
                    spec = getChildMeasureSpec(widthMeasureSpec, 0, ViewGroup.LayoutParams.WRAP_CONTENT);
                    break;
                case ViewGroup.LayoutParams.MATCH_PARENT:
                    // AOSP TableRow.getColumnsWidths, verbatim: a
                    // MATCH_PARENT cell is measured against the HEIGHT
                    // spec's size here (not the width spec) -- a
                    // longstanding upstream quirk mirrored exactly rather
                    // than "corrected", since matching AOSP means matching
                    // its observable behavior, quirks included. (Our
                    // MeasureSpec has no makeSafeMeasureSpec overflow
                    // guard, so this uses the plain, equivalent-for-any-
                    // realistic-input makeMeasureSpec.)
                    spec = MeasureSpec.makeMeasureSpec(
                            MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.UNSPECIFIED);
                    break;
                default:
                    // ⚠ DO NOT MEASURE A FIXED-WIDTH CELL TO LEARN ITS WIDTH.
                    // AOSP's TableRow.getColumnsWidths measures at
                    // EXACTLY(lp.width), and a spec-honouring View returns
                    // exactly that -- so the measure is pure ceremony and the
                    // declared width IS the answer. Taking it directly makes
                    // this immune to a child that DISOBEYS its spec.
                    //
                    // One does: ImageView.onMeasure deliberately breaks the
                    // EXACTLY contract at size 0 (`wSize == 0 && iw > 0 -> iw`,
                    // a v1.0.131 workaround for ?attr/itemIconSize resolving to
                    // 0). So in a TableLayout every <ImageButton> cell declared
                    // `layout_width="0dp"` injected its DRAWABLE'S INTRINSIC
                    // WIDTH into mMaxWidths while <Button> cells correctly
                    // contributed 0 -- and applyColumnWidths then handed the
                    // whole row those unequal widths.
                    // Measured on opencalculator (density 2, avail = 808-32 =
                    // 776): dot.xml 24dp->48px in col 1 and backspace.xml
                    // 45dp->90px in col 2 predict columns
                    // 159/207/249/159 against a measured 159/205/248/160 --
                    // error <=2px with zero fitting. AOSP is a uniform 776/4 =
                    // 194, which is what the emulator draws.
                    widths[i] = lp.width
                        + ((lp instanceof ViewGroup.MarginLayoutParams mm) ? mm.leftMargin : 0)
                        + ((lp instanceof ViewGroup.MarginLayoutParams mm) ? mm.rightMargin : 0);
                    continue;
            }
            child.measure(spec, spec);

            final int leftMargin  = (lp instanceof ViewGroup.MarginLayoutParams m) ? m.leftMargin  : 0;
            final int rightMargin = (lp instanceof ViewGroup.MarginLayoutParams m) ? m.rightMargin : 0;
            widths[i] = child.getMeasuredWidth() + leftMargin + rightMargin;
        }
        return widths;
    }

    /** AOSP TableLayout.shrinkAndStretchColumns. */
    private void shrinkAndStretchColumns(int widthMeasureSpec) {
        if (mMaxWidths == null) return;

        int totalWidth = 0;
        for (int width : mMaxWidths) totalWidth += width;

        final int size = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();

        if ((totalWidth > size) && (mShrinkAllColumns || !mShrinkableColumns.isEmpty())) {
            mutateColumnsWidth(mShrinkableColumns, mShrinkAllColumns, size, totalWidth);
        } else if ((totalWidth < size) && (mStretchAllColumns || !mStretchableColumns.isEmpty())) {
            mutateColumnsWidth(mStretchableColumns, mStretchAllColumns, size, totalWidth);
        }
    }

    /** AOSP TableLayout.mutateColumnsWidth: fairly redistributes the
     *  difference between the available width and the summed column
     *  widths across the flagged (or all) columns. Ported from
     *  SparseBooleanArray key/value iteration to TreeMap entrySet
     *  iteration (same ascending-key order). */
    private void mutateColumnsWidth(java.util.TreeMap<Integer, Boolean> columns,
            boolean allColumns, int size, int totalWidth) {
        final int[] maxWidths = mMaxWidths;
        final int length = maxWidths.length;
        final int count = allColumns ? length : columns.size();
        // AOSP's equivalent divides totalExtraSpace by count unconditionally
        // here; count==0 can only happen if the first row had zero cells,
        // an edge case worth a defensive return rather than an
        // ArithmeticException regression on a class that used to be an
        // inert no-op.
        if (count == 0) return;
        final int totalExtraSpace = size - totalWidth;
        int extraSpace = totalExtraSpace / count;

        // Column widths are changing -- the rows must re-measure their
        // cells at the new constraint later in this same onMeasure pass
        // (applyColumnWidths() + super.onMeasure(), right after this
        // method returns).
        final int nbChildren = getChildCount();
        for (int i = 0; i < nbChildren; i++) {
            final View child = getChildAt(i);
            if (child instanceof TableRow) child.forceLayout();
        }

        int skipped = 0;
        if (!allColumns) {
            for (java.util.Map.Entry<Integer, Boolean> e : columns.entrySet()) {
                final int column = e.getKey();
                if (e.getValue()) {
                    if (column < length) {
                        maxWidths[column] += extraSpace;
                    } else {
                        skipped++;
                    }
                }
            }
        } else {
            for (int i = 0; i < count; i++) {
                maxWidths[i] += extraSpace;
            }
            return;
        }

        if (skipped > 0 && skipped < count) {
            extraSpace = skipped * extraSpace / (count - skipped);
            for (java.util.Map.Entry<Integer, Boolean> e : columns.entrySet()) {
                final int column = e.getKey();
                if (e.getValue() && column < length) {
                    if (extraSpace > maxWidths[column]) {
                        maxWidths[column] = 0;
                    } else {
                        maxWidths[column] += extraSpace;
                    }
                }
            }
        }
    }

    /** AOSP TableLayout.measureChildBeforeLayout: "when the measured child is
     *  a table row, we force the width of its cells to the width of the
     *  corresponding column" -- by handing the row the column widths, which
     *  TableRow.measureChildBeforeLayout applies (minus each cell's margins).
     *  v1.0.1643: this used to write every cell's lp.width, which (a) is not
     *  AOSP (the app's declared width must survive) and (b) grew the column
     *  by the cell's margins on every measure once margins were inflated. */
    private void applyColumnWidths() {
        if (mMaxWidths == null) return;
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (!(child instanceof TableRow row)) continue;
            if (LAYOUT_AOSP_1643) { row.setColumnsWidthConstraints(mMaxWidths); continue; }
            // Pre-1643 path (MACAPK_LAYOUT_AOSP_1643=0 bisect gate only).
            final int numColumns = row.getVirtualChildCount();
            final int limit = Math.min(numColumns, mMaxWidths.length);
            for (int col = 0; col < limit; col++) {
                final View cell = row.getVirtualChildAt(col);
                if (cell == null) continue;
                final ViewGroup.LayoutParams lp = cell.getLayoutParams();
                if (lp == null) continue;
                final int span = (lp instanceof TableRow.LayoutParams trlp) ? trlp.span : 1;
                if (span != 1) continue;
                lp.width = mMaxWidths[col];
            }
        }
    }
    /** See LayoutInflater.LAYOUT_AOSP_1643 (bisect gate, default ON). */
    private static final boolean LAYOUT_AOSP_1643 =
        !"0".equals(System.getenv("MACAPK_LAYOUT_AOSP_1643"));

    /** Inner LayoutParams type used by TableLayout's direct children
     *  (typically TableRow). AOSP has a richer set (span, weight, etc.
     *  belong to TableRow.LayoutParams instead); we mirror the field a
     *  caller is most likely to read/set plus AOSP's documented width
     *  contract: "The children of a TableLayout cannot specify the
     *  layout_width attribute. Width is always MATCH_PARENT." -- so an
     *  explicit w argument is discarded, matching AOSP's own constructor
     *  bodies (`super(MATCH_PARENT, h)`), not passed through. */
    /** v1.0.1643: AOSP checkLayoutParams, so addView converts a foreign
     *  LayoutParams into this container's own kind (ViewGroup.addView ->
     *  generateLayoutParams). Gate MACAPK_CHECK_LAYOUT_PARAMS=0 restores the old
     *  accept-anything answer for an A/B. */
    @Override
    protected boolean checkLayoutParams(android.view.ViewGroup.LayoutParams p) {
        if (!MACAPK_CHECK_LP) return true;
        return p instanceof TableLayout.LayoutParams;
    }
    private static final boolean MACAPK_CHECK_LP =
        !"0".equals(System.getenv("MACAPK_CHECK_LAYOUT_PARAMS"));
    /** AOSP TableLayout.generateLayoutParams(ViewGroup.LayoutParams). */
    @Override
    protected LinearLayout.LayoutParams generateLayoutParams(android.view.ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    /** AOSP TableLayout.generateLayoutParams(AttributeSet) /
     *  generateDefaultLayoutParams: rows carry TableLayout.LayoutParams (width
     *  always MATCH_PARENT). See TableRow for why this matters since v1.0.1643. */
    @Override
    public android.view.ViewGroup.LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
        return new TableLayout.LayoutParams(getContext(), attrs);
    }
    @Override
    public LinearLayout.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    public static class LayoutParams extends LinearLayout.LayoutParams {
        public LayoutParams(int w, int h) {
            super(android.view.ViewGroup.LayoutParams.MATCH_PARENT, h);
        }
        public LayoutParams(int w, int h, float weight) {
            super(android.view.ViewGroup.LayoutParams.MATCH_PARENT, h, weight);
        }
        public LayoutParams() {
            super(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                  android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        /** AOSP TableLayout.LayoutParams(Context, AttributeSet): width is always
         *  MATCH_PARENT, height WRAP_CONTENT unless the XML says otherwise. */
        public LayoutParams(android.content.Context c, android.util.AttributeSet attrs) {
            super(c, attrs);
            width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        /** AOSP TableLayout.LayoutParams(ViewGroup.LayoutParams): width is
         *  always MATCH_PARENT. */
        public LayoutParams(android.view.ViewGroup.LayoutParams p) {
            super(p);
            width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        }
        public LayoutParams(android.view.ViewGroup.MarginLayoutParams source) {
            super(source);
            width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            if (source instanceof TableLayout.LayoutParams) {
                weight = ((TableLayout.LayoutParams) source).weight;
            }
        }
    }
}
