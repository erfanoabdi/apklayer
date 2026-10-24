// android.widget.GridLayout -- a ViewGroup that arranges children in a grid
// of rows and columns. Was Absent (common in forms / settings). The full
// AOSP GridLayout solver (flexible spans, weighted alignment, arc-based
// constraint propagation) is large; this implements the public API surface
// faithfully (Spec/Alignment factories, row/column counts, orientation,
// spacing) with a uniform row-height / per-column-width grid placement that
// honors explicit rowSpec/columnSpec column indices and flows the rest. AOSP:
//   frameworks/base/core/java/android/widget/GridLayout.java
//   public class GridLayout extends ViewGroup
package android.widget;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

public class GridLayout extends ViewGroup {
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;
    public static final int UNDEFINED = Integer.MIN_VALUE;
    public static final int ALIGN_BOUNDS = 0;
    public static final int ALIGN_MARGINS = 1;

    private int mOrientation = HORIZONTAL;
    private int mColumnCount = UNDEFINED;
    private int mRowCount = UNDEFINED;
    private boolean mUseDefaultMargins;
    private int mAlignmentMode = ALIGN_MARGINS;
    private boolean mColumnOrderPreserved = true;
    private boolean mRowOrderPreserved = true;

    public GridLayout(Context context) { super(context); }
    public GridLayout(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public GridLayout(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public GridLayout(Context context, android.util.AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public int getOrientation() { return mOrientation; }
    public void setOrientation(int orientation) { mOrientation = orientation; requestLayout(); }
    public int getColumnCount() { return mColumnCount == UNDEFINED ? inferColumns() : mColumnCount; }
    public void setColumnCount(int columnCount) { mColumnCount = columnCount; requestLayout(); }
    public int getRowCount() { return mRowCount == UNDEFINED ? inferRows() : mRowCount; }
    public void setRowCount(int rowCount) { mRowCount = rowCount; requestLayout(); }
    public boolean getUseDefaultMargins() { return mUseDefaultMargins; }
    public void setUseDefaultMargins(boolean useDefaultMargins) { mUseDefaultMargins = useDefaultMargins; }
    public int getAlignmentMode() { return mAlignmentMode; }
    public void setAlignmentMode(int alignmentMode) { mAlignmentMode = alignmentMode; }
    public boolean isColumnOrderPreserved() { return mColumnOrderPreserved; }
    public void setColumnOrderPreserved(boolean columnOrderPreserved) { mColumnOrderPreserved = columnOrderPreserved; }
    public boolean isRowOrderPreserved() { return mRowOrderPreserved; }
    public void setRowOrderPreserved(boolean rowOrderPreserved) { mRowOrderPreserved = rowOrderPreserved; }

    private int inferColumns() {
        // AOSP infers from the max column index occupied; default to child
        // count when nothing is specified (single row).
        int max = 1;
        for (int i = 0; i < getChildCount(); i++) {
            LayoutParams lp = lp(getChildAt(i));
            if (lp.columnSpec.span.max() > max) max = lp.columnSpec.span.max();
        }
        return Math.max(max, mOrientation == HORIZONTAL ? getChildCount() : 1);
    }
    private int inferRows() {
        int count = getChildCount();
        int cols = Math.max(1, getColumnCount());
        return (count + cols - 1) / cols;
    }

    private LayoutParams lp(View child) {
        ViewGroup.LayoutParams p = child.getLayoutParams();
        if (p instanceof LayoutParams) return (LayoutParams) p;
        return new LayoutParams();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();
        int cols = Math.max(1, getColumnCount());
        int rows = Math.max(1, (count + cols - 1) / cols);

        int avail = MeasureSpec.getSize(widthMeasureSpec)
                - getPaddingLeft() - getPaddingRight();
        int cellW = cols > 0 ? Math.max(1, avail / cols) : avail;

        int rowHeight = 0;
        int totalHeight = 0;
        int col = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChild(child,
                    MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            col++;
            if (col >= cols) { totalHeight += rowHeight; rowHeight = 0; col = 0; }
        }
        if (col != 0) totalHeight += rowHeight;
        totalHeight += getPaddingTop() + getPaddingBottom();

        setMeasuredDimension(
                resolveSize(MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec),
                resolveSize(totalHeight, heightMeasureSpec));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = getChildCount();
        int cols = Math.max(1, getColumnCount());
        int avail = getWidth() - getPaddingLeft() - getPaddingRight();
        int cellW = cols > 0 ? Math.max(1, avail / cols) : avail;

        int x = getPaddingLeft();
        int y = getPaddingTop();
        int col = 0;
        int rowHeight = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();
            int cx = x + col * cellW;
            child.layout(cx, y, cx + cw, y + ch);
            rowHeight = Math.max(rowHeight, ch);
            col++;
            if (col >= cols) { y += rowHeight; rowHeight = 0; col = 0; }
        }
    }

    // AOSP frameworks/base core/java/android/widget/GridLayout.java (~line 898):
    // covariant override narrowing ViewGroup.LayoutParams to GridLayout.LayoutParams
    // -- the body already constructs and returns the narrower type, only the
    // declared descriptor was too wide. Found by debug/framework_lint.py's
    // class-shape diff against android.jar.
    @Override public GridLayout.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    /** AOSP GridLayout.generateLayoutParams(AttributeSet), android14-release
     *  GridLayout.java:902-905 (method at :903):
     *      @Override
     *      public LayoutParams generateLayoutParams(AttributeSet attrs) {
     *          return new LayoutParams(getContext(), attrs);
     *      }
     *  Without it GridLayout inherited ViewGroup's bare `new LayoutParams()`, and
     *  because GridLayout is none of the containers LayoutInflater.buildLayoutParams
     *  special-cases, its else-branch adopted that bare LP for every DIRECT XML
     *  child, not only for <include> sites: layout_margin* had nowhere to land and
     *  addView's checkLayoutParams conversion (v1.0.1643) carried only width and
     *  height across. Same defect as LinearLayout's
     *  (docs/wikipedia-pageactivity-classcastexception_2026-09-26.md). Census of
     *  ../Apks on 2026-09-26: the only framework <GridLayout> with XML children is
     *  anuto's fragment_tower_build, whose four children declare nothing beyond
     *  width and height, so no corpus layout changes; the gate's synthetic row is
     *  the instrument.
     *
     *  Declared as ViewGroup.LayoutParams rather than AOSP's covariant
     *  GridLayout.LayoutParams so the bisect gate can hand back the base answer
     *  (0 corpus references to the covariant descriptor, debug/apishape_census.py).
     *
     *  BISECT GATE (default ON): MACAPK_GRID_GENLP_ATTRS=0 restores the inherited
     *  base answer here AND the unseeded 0x0 LayoutParams(Context, AttributeSet). */
    @Override
    public ViewGroup.LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
        if (!LayoutParams.MACAPK_GENLP_ATTRS) return super.generateLayoutParams(attrs);
        return new LayoutParams(getContext(), attrs);
    }

    // ---- Spec / Alignment public surface ----
    public static final Alignment TOP = new Alignment();
    public static final Alignment BOTTOM = new Alignment();
    public static final Alignment LEFT = new Alignment();
    public static final Alignment RIGHT = new Alignment();
    public static final Alignment CENTER = new Alignment();
    public static final Alignment BASELINE = new Alignment();
    public static final Alignment FILL = new Alignment();
    public static final Alignment START = new Alignment();
    public static final Alignment END = new Alignment();

    public static class Alignment {
        Alignment() {}
    }

    static class Interval {
        final int min, max;
        Interval(int min, int max) { this.min = min; this.max = max; }
        int min() { return min; }
        int max() { return max; }
        int size() { return max - min; }
    }

    public static class Spec {
        final boolean startDefined;
        final Interval span;
        final Alignment alignment;
        final float weight;

        Spec(boolean startDefined, Interval span, Alignment alignment, float weight) {
            this.startDefined = startDefined;
            this.span = span;
            this.alignment = alignment;
            this.weight = weight;
        }
        public Alignment getAlignment() { return alignment; }
        public float getWeight() { return weight; }
    }

    public static Spec spec(int start, int size, Alignment alignment, float weight) {
        boolean defined = start != UNDEFINED;
        int s = defined ? start : 0;
        return new Spec(defined, new Interval(s, s + size), alignment, weight);
    }
    public static Spec spec(int start, int size, Alignment alignment) {
        return spec(start, size, alignment, 0f);
    }
    public static Spec spec(int start, Alignment alignment, float weight) {
        return spec(start, 1, alignment, weight);
    }
    public static Spec spec(int start, Alignment alignment) {
        return spec(start, 1, alignment, 0f);
    }
    public static Spec spec(int start, int size, float weight) {
        return spec(start, size, FILL, weight);
    }
    public static Spec spec(int start, float weight) {
        return spec(start, 1, weight);
    }
    public static Spec spec(int start, int size) {
        return spec(start, size, FILL, 0f);
    }
    public static Spec spec(int start) {
        return spec(start, 1);
    }

    /** v1.0.1643: AOSP checkLayoutParams, so addView converts a foreign
     *  LayoutParams into this container's own kind (ViewGroup.addView ->
     *  generateLayoutParams). Gate MACAPK_CHECK_LAYOUT_PARAMS=0 restores the old
     *  accept-anything answer for an A/B. */
    @Override
    protected boolean checkLayoutParams(android.view.ViewGroup.LayoutParams p) {
        if (!MACAPK_CHECK_LP) return true;
        return p instanceof GridLayout.LayoutParams;
    }
    private static final boolean MACAPK_CHECK_LP =
        !"0".equals(System.getenv("MACAPK_CHECK_LAYOUT_PARAMS"));

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        /** The MACAPK_GRID_GENLP_ATTRS bisect gate (see
         *  GridLayout.generateLayoutParams(AttributeSet)). */
        static final boolean MACAPK_GENLP_ATTRS =
            !"0".equals(System.getenv("MACAPK_GRID_GENLP_ATTRS"));

        private static final Interval DEFAULT_SPAN = new Interval(UNDEFINED, UNDEFINED + 1);

        public Spec rowSpec = new Spec(false, DEFAULT_SPAN, FILL, 0f);
        public Spec columnSpec = new Spec(false, DEFAULT_SPAN, FILL, 0f);

        public LayoutParams(Spec rowSpec, Spec columnSpec) {
            super(WRAP_CONTENT, WRAP_CONTENT);
            this.rowSpec = rowSpec;
            this.columnSpec = columnSpec;
        }
        public LayoutParams() { super(WRAP_CONTENT, WRAP_CONTENT); }
        public LayoutParams(ViewGroup.LayoutParams params) { super(params); }
        public LayoutParams(ViewGroup.MarginLayoutParams params) { super(params); }
        public LayoutParams(LayoutParams source) {
            super(source);
            this.rowSpec = source.rowSpec;
            this.columnSpec = source.columnSpec;
        }
        /** AOSP GridLayout.LayoutParams(Context, AttributeSet), android14-release
         *  GridLayout.java:2151-2155, overrides setBaseAttributes so an absent size
         *  is DEFAULT_WIDTH / DEFAULT_HEIGHT, both WRAP_CONTENT (:2024-2025, :2215-2219):
         *      this.width = attributes.getLayoutDimension(widthAttr, DEFAULT_WIDTH);
         *      this.height = attributes.getLayoutDimension(heightAttr, DEFAULT_HEIGHT);
         *  (a GridLayout child, unlike most, may omit layout_width/height). Ours does
         *  not parse; the inflater's attribute walk fills whatever the XML says, so
         *  the seed is exactly AOSP's answer for what it does not say. Not ported
         *  here: AOSP's UNDEFINED margin default (reInitSuper) and the
         *  layout_row/column/span/weight specs (init). Our grid placement reads
         *  neither, and our no-arg LayoutParams() uses 0 margins too.
         *  MACAPK_GRID_GENLP_ATTRS=0 restores the old unseeded 0x0. */
        public LayoutParams(Context context, android.util.AttributeSet attrs) {
            super(context, attrs);
            if (MACAPK_GENLP_ATTRS) { width = WRAP_CONTENT; height = WRAP_CONTENT; }
        }

        // AOSP folds gravity into the row/column alignment; our placement is
        // grid-uniform so we accept and ignore the value (kept for the API).
        public void setGravity(int gravity) { }
    }

    /** AOSP GridLayout.generateLayoutParams(ViewGroup.LayoutParams) -- keeps
     *  the row/column Specs when the source already is a GridLayout.LayoutParams.
     *  No @Override: see the note in LinearLayout's copy. */
    protected GridLayout.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        if (p instanceof GridLayout.LayoutParams) {
            return new GridLayout.LayoutParams((GridLayout.LayoutParams) p);
        }
        if (p instanceof ViewGroup.MarginLayoutParams) {
            return new GridLayout.LayoutParams((ViewGroup.MarginLayoutParams) p);
        }
        return new GridLayout.LayoutParams(p);
    }
}
