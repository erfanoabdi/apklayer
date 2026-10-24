// android.widget.AbsSpinner -- shared base of Spinner/Gallery. An
// AdapterView<SpinnerAdapter> that shows ONE selected child at a time. Was a
// bare `extends Object` stub (broke instanceof AdapterView and setAdapter).
// Now a real AdapterView that measures/lays out the selected item view.
// AOSP: frameworks/base/core/java/android/widget/AbsSpinner.java
//   public abstract class AbsSpinner extends AdapterView<SpinnerAdapter>
//
// Wave 5 (2026-09-26): the measure / layout / selection model is AOSP
// android14-release AbsSpinner + Spinner.layout, behind MACAPK_SPINNER_RECYCLE
// (see its comment). The legacy bodies below each `if (!MACAPK_SPINNER_RECYCLE)`
// are kept verbatim so the gate restores the exact pre-change path.
package android.widget;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;

public abstract class AbsSpinner extends AdapterView<SpinnerAdapter> {
    /** Labelled bisect gate (wave 5, 2026-09-26): AOSP AbsSpinner's RecycleBin,
     *  onMeasure (android14-release AbsSpinner.java:178-247), getSelectedView
     *  (:317-319) and Spinner.layout / makeView / setUpChild (Spinner.java
     *  :631-758). The legacy getSelectedView() answered
     *  `mAdapter.getView(pos, null, this)` -- a FRESH inflate on every call --
     *  and both onMeasure (when the height was not EXACTLY) and onLayout (after
     *  removeAllViewsInLayout) called it, so every traversal inflated several
     *  item views per spinner, the on-screen child changed identity on every
     *  layout, and getSelectedView() / onItemSelected handed the app a DETACHED
     *  view that was never on screen. AOSP inflates at most one view per
     *  spinner per forced measure (RecycleBin.get REMOVES the entry, so a second
     *  measure in the same traversal takes the same view back), and layout
     *  recycles the current child into the bin and takes it straight back out,
     *  so the shown child keeps its identity. MEASURED victim:
     *  org.bobstuff.bobball's level-select screen, 7 fresh inflates per spinner
     *  per traversal (13.4 ms of a 13.7 ms traversal), and the step-1 dumps show
     *  the same spinner's child as FrameLayout@35efb834 then @4f73c916
     *  (docs/fleet-misc-shatteredpd-retrowars-bobball-bouncy-unciv_2026-09-26.md,
     *  E1). MACAPK_SPINNER_RECYCLE=0 restores the legacy AbsSpinner and Spinner
     *  (and disables the two sub-gates below). Offline gate:
     *  debug/w5widgets_gate.sh (rows sp-*). */
    static final boolean MACAPK_SPINNER_RECYCLE =
            !"0".equals(System.getenv("MACAPK_SPINNER_RECYCLE"));
    /** Sub-gate, only meaningful with MACAPK_SPINNER_RECYCLE on: the WIDTH half
     *  of the AOSP measure. AbsSpinner.onMeasure answers
     *  resolveSizeAndState(childWidth + padding, widthSpec) (:238-244), and
     *  Spinner.onMeasure widens an AT_MOST spinner to its widest item
     *  (measureContentWidth, :613-622 / :859-899). The legacy measure took the
     *  whole spec width, so a wrap_content spinner filled its parent.
     *  MACAPK_SPINNER_WIDTH=0 keeps the legacy spec-size width inside the
     *  ported measure. This is the one pixel-visible part of the port, hence
     *  its own switch. */
    static final boolean MACAPK_SPINNER_WIDTH = MACAPK_SPINNER_RECYCLE
            && !"0".equals(System.getenv("MACAPK_SPINNER_WIDTH"));
    /** Sub-gate, only meaningful with MACAPK_SPINNER_RECYCLE on: AOSP's
     *  selection notification. AOSP fires onItemSelected from
     *  AdapterView.checkSelectionChanged at the END of a layout, POSTED when in
     *  layout (AdapterView.selectionChanged), with the view actually on screen
     *  -- including the famous first callback for position 0 after setAdapter,
     *  because setAdapter resets mOldSelectedPosition to INVALID_POSITION.
     *  setSelection(int) itself only records the next position and requests a
     *  layout. The legacy code fired synchronously inside setSelection with a
     *  freshly inflated detached view, and never fired for the initial
     *  selection. MACAPK_SPINNER_SELECT_NOTIFY=0 keeps that legacy synchronous
     *  notification on top of the ported measure/layout. */
    static final boolean MACAPK_SPINNER_SELECT_NOTIFY = MACAPK_SPINNER_RECYCLE
            && !"0".equals(System.getenv("MACAPK_SPINNER_SELECT_NOTIFY"));

    SpinnerAdapter mAdapter;
    int mSelectedPosition = INVALID_POSITION;

    // ---- AOSP AdapterView / AbsSpinner selection state (MACAPK_SPINNER_RECYCLE).
    // Our AdapterView does not carry these, so the spinner keeps its own copy;
    // the names and transitions are AdapterView's.
    int mNextSelectedPosition = INVALID_POSITION;
    long mNextSelectedRowId = INVALID_ROW_ID;
    long mSelectedRowId = INVALID_ROW_ID;
    int mOldSelectedPosition = INVALID_POSITION;
    long mOldSelectedRowId = INVALID_ROW_ID;
    int mItemCount;
    int mOldItemCount;
    boolean mDataChanged;
    boolean mNeedSync;
    long mSyncRowId = INVALID_ROW_ID;
    int mSyncPosition;
    int mSyncMode;
    static final int SYNC_SELECTED_POSITION = 0;
    static final int SYNC_FIRST_POSITION = 1;
    /** AdapterView.SYNC_MAX_DURATION_MILLIS: the findSyncPosition time budget. */
    static final int SYNC_MAX_DURATION_MILLIS = 100;
    boolean mInLayout;
    boolean mBlockLayoutRequests;
    int mHeightMeasureSpec;
    int mWidthMeasureSpec;
    int mSelectionLeftPadding = 0;
    int mSelectionTopPadding = 0;
    int mSelectionRightPadding = 0;
    int mSelectionBottomPadding = 0;
    final android.graphics.Rect mSpinnerPadding = new android.graphics.Rect();
    final RecycleBin mRecycler = new RecycleBin();
    private android.graphics.Rect mTouchFrame;
    private SelectionNotifier mSelectionNotifier;
    private SelectionNotifier mPendingSelectionNotifier;

    private final DataSetObserver mObserver = new DataSetObserver() {
        @Override public void onChanged() {
            if (MACAPK_SPINNER_RECYCLE) {
                // AOSP AdapterView.AdapterDataSetObserver.onChanged.
                mDataChanged = true;
                mOldItemCount = mItemCount;
                mItemCount = mAdapter != null ? mAdapter.getCount() : 0;
                rememberSyncState();
                requestLayout();
                return;
            }
            if (mSelectedPosition == INVALID_POSITION && getCount() > 0) {
                mSelectedPosition = 0;
            }
            requestLayout();
            invalidate();
        }
        @Override public void onInvalidated() {
            if (MACAPK_SPINNER_RECYCLE) {
                // AOSP AdapterView.AdapterDataSetObserver.onInvalidated.
                mDataChanged = true;
                mOldItemCount = mItemCount;
                mItemCount = 0;
                mSelectedPosition = INVALID_POSITION;
                mSelectedRowId = INVALID_ROW_ID;
                mNextSelectedPosition = INVALID_POSITION;
                mNextSelectedRowId = INVALID_ROW_ID;
                selectedPosition = INVALID_POSITION;
                mNeedSync = false;
                requestLayout();
                return;
            }
            requestLayout(); invalidate();
        }
    };

    public AbsSpinner(Context c) { super(c); }
    public AbsSpinner(Context c, android.util.AttributeSet a) { super(c, a); }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public AbsSpinner(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public AbsSpinner(Context c, android.util.AttributeSet a, int s) { super(c, a, s); }

    @Override public SpinnerAdapter getAdapter() { return mAdapter; }

    @Override public void setAdapter(SpinnerAdapter adapter) {
        if (MACAPK_SPINNER_RECYCLE) { macapkSetAdapterAosp(adapter); return; }
        if (mAdapter != null) {
            try { mAdapter.unregisterDataSetObserver(mObserver); } catch (Throwable ignored) {}
        }
        mAdapter = adapter;
        this.adapter = adapter;
        if (adapter != null) {
            try { adapter.registerDataSetObserver(mObserver); } catch (Throwable ignored) {}
            mSelectedPosition = adapter.getCount() > 0 ? 0 : INVALID_POSITION;
        } else {
            mSelectedPosition = INVALID_POSITION;
        }
        requestLayout();
    }

    /** AOSP AbsSpinner.setAdapter (:114-154). The observer registration keeps
     *  the legacy try/catch: a DataSetObservable throws IllegalStateException on
     *  a double registration, and that must not abort an app's setAdapter. */
    private void macapkSetAdapterAosp(SpinnerAdapter adapter) {
        if (null != mAdapter) {
            try { mAdapter.unregisterDataSetObserver(mObserver); } catch (Throwable ignored) {}
            resetList();
        }
        mAdapter = adapter;
        this.adapter = adapter;
        mOldSelectedPosition = INVALID_POSITION;
        mOldSelectedRowId = INVALID_ROW_ID;
        if (mAdapter != null) {
            mOldItemCount = mItemCount;
            mItemCount = mAdapter.getCount();
            try { mAdapter.registerDataSetObserver(mObserver); } catch (Throwable ignored) {}
            final int position = mItemCount > 0 ? 0 : INVALID_POSITION;
            setSelectedPositionInt(position);
            setNextSelectedPositionInt(position);
            if (mItemCount == 0) {
                // Nothing selected
                checkSelectionChanged();
            }
        } else {
            resetList();
            // Nothing selected
            checkSelectionChanged();
        }
        requestLayout();
    }

    /** AOSP AbsSpinner.resetList (:156-167): clear all views. */
    void resetList() {
        mDataChanged = false;
        mNeedSync = false;
        removeAllViewsInLayout();
        mOldSelectedPosition = INVALID_POSITION;
        mOldSelectedRowId = INVALID_ROW_ID;
        setSelectedPositionInt(INVALID_POSITION);
        setNextSelectedPositionInt(INVALID_POSITION);
        invalidate();
    }

    @Override public int getCount() {
        if (MACAPK_SPINNER_RECYCLE) return mItemCount;   // AOSP :344-346
        return mAdapter != null ? mAdapter.getCount() : 0;
    }

    /** AOSP AdapterView.getSelectedItemPosition answers mNextSelectedPosition:
     *  the position the app asked for, before the layout that shows it. */
    @Override public int getSelectedItemPosition() {
        return MACAPK_SPINNER_RECYCLE ? mNextSelectedPosition : mSelectedPosition;
    }

    @Override public View getSelectedView() {
        if (MACAPK_SPINNER_RECYCLE) {
            // AOSP :317-319: the child actually on screen.
            if (mItemCount > 0 && mSelectedPosition >= 0) {
                return getChildAt(mSelectedPosition - mFirstPosition);
            }
            return null;
        }
        if (mAdapter != null && mSelectedPosition >= 0
                && mSelectedPosition < mAdapter.getCount()) {
            return mAdapter.getView(mSelectedPosition, null, this);
        }
        return null;
    }

    @Override public void setSelection(int position) {
        if (MACAPK_SPINNER_RECYCLE) {
            // AOSP :289-293.
            setNextSelectedPositionInt(position);
            requestLayout();
            invalidate();
            if (!MACAPK_SPINNER_SELECT_NOTIFY) macapkLegacyNotify(position);
            return;
        }
        mSelectedPosition = position;
        super.setSelection(position);
        requestLayout();
        invalidate();
        if (onItemSelectedListener != null && mAdapter != null
                && position >= 0 && position < mAdapter.getCount()) {
            onItemSelectedListener.onItemSelected(this, getSelectedView(),
                    position, mAdapter.getItemId(position));
        }
    }
    public void setSelection(int position, boolean animate) {
        if (MACAPK_SPINNER_RECYCLE) {
            // AOSP :281-286: animate only if the position is already on screen.
            final boolean shouldAnimate = animate && mFirstPosition <= position
                    && position <= mFirstPosition + getChildCount() - 1;
            setSelectionInt(position, shouldAnimate);
            if (!MACAPK_SPINNER_SELECT_NOTIFY) macapkLegacyNotify(position);
            return;
        }
        setSelection(position);
    }

    /** MACAPK_SPINNER_SELECT_NOTIFY=0 only: the legacy synchronous callback,
     *  with the legacy freshly inflated view. */
    private void macapkLegacyNotify(int position) {
        if (onItemSelectedListener != null && mAdapter != null
                && position >= 0 && position < mAdapter.getCount()) {
            onItemSelectedListener.onItemSelected(this,
                    mAdapter.getView(position, null, this),
                    position, mAdapter.getItemId(position));
        }
    }

    /** AOSP AbsSpinner.setSelectionInt (:303-312): lay out the new selection
     *  immediately (the layout requests it would make are blocked). */
    void setSelectionInt(int position, boolean animate) {
        if (position != mOldSelectedPosition) {
            mBlockLayoutRequests = true;
            final int delta = position - mSelectedPosition;
            setNextSelectedPositionInt(position);
            layout(delta, animate);
            mBlockLayoutRequests = false;
        }
    }

    /** AOSP AbsSpinner.requestLayout (:332-337). mBlockLayoutRequests is only
     *  ever raised on the ported path, so the legacy path is unaffected. */
    @Override public void requestLayout() {
        if (!mBlockLayoutRequests) {
            super.requestLayout();
        }
    }

    /** AOSP AbsSpinner.generateDefaultLayoutParams (:259-263). Public here
     *  because our ViewGroup declares the base public. */
    @Override public ViewGroup.LayoutParams generateDefaultLayoutParams() {
        if (!MACAPK_SPINNER_RECYCLE) return super.generateDefaultLayoutParams();
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MACAPK_SPINNER_RECYCLE) { macapkOnMeasureAosp(widthMeasureSpec, heightMeasureSpec); return; }
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            View sel = getSelectedView();
            if (sel != null) {
                sel.measure(widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                height = sel.getMeasuredHeight()
                        + getPaddingTop() + getPaddingBottom();
            }
        }
        setMeasuredDimension(width, height);
    }

    /** AOSP AbsSpinner.onMeasure (android14-release :178-247), verbatim apart
     *  from the MACAPK_SPINNER_WIDTH sub-gate on the last width line. */
    private void macapkOnMeasureAosp(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize;
        int heightSize;

        final int pl = getPaddingLeft(), pt = getPaddingTop();
        final int pr = getPaddingRight(), pb = getPaddingBottom();
        mSpinnerPadding.left = pl > mSelectionLeftPadding ? pl : mSelectionLeftPadding;
        mSpinnerPadding.top = pt > mSelectionTopPadding ? pt : mSelectionTopPadding;
        mSpinnerPadding.right = pr > mSelectionRightPadding ? pr : mSelectionRightPadding;
        mSpinnerPadding.bottom = pb > mSelectionBottomPadding ? pb : mSelectionBottomPadding;

        if (mDataChanged) {
            handleDataChanged();
        }

        int preferredHeight = 0;
        int preferredWidth = 0;
        boolean needsMeasuring = true;

        final int selectedPosition = getSelectedItemPosition();
        if (selectedPosition >= 0 && mAdapter != null && selectedPosition < mAdapter.getCount()) {
            // Try looking in the recycler. (Maybe we were measured once already)
            View view = mRecycler.get(selectedPosition);
            if (view == null) {
                // Make a new one
                view = mAdapter.getView(selectedPosition, null, this);
                if (view != null && view.getImportantForAccessibility()
                        == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
                    view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
                }
            }
            if (view != null) {
                // Put in recycler for re-measuring and/or layout
                mRecycler.put(selectedPosition, view);
                if (view.getLayoutParams() == null) {
                    mBlockLayoutRequests = true;
                    view.setLayoutParams(generateDefaultLayoutParams());
                    mBlockLayoutRequests = false;
                }
                measureChild(view, widthMeasureSpec, heightMeasureSpec);
                preferredHeight = getChildHeight(view) + mSpinnerPadding.top + mSpinnerPadding.bottom;
                preferredWidth = getChildWidth(view) + mSpinnerPadding.left + mSpinnerPadding.right;
                needsMeasuring = false;
            }
        }

        if (needsMeasuring) {
            // No views -- just use padding
            preferredHeight = mSpinnerPadding.top + mSpinnerPadding.bottom;
            if (widthMode == MeasureSpec.UNSPECIFIED) {
                preferredWidth = mSpinnerPadding.left + mSpinnerPadding.right;
            }
        }

        preferredHeight = Math.max(preferredHeight, getSuggestedMinimumHeight());
        preferredWidth = Math.max(preferredWidth, getSuggestedMinimumWidth());

        heightSize = resolveSizeAndState(preferredHeight, heightMeasureSpec, 0);
        widthSize = MACAPK_SPINNER_WIDTH
                ? resolveSizeAndState(preferredWidth, widthMeasureSpec, 0)
                : MeasureSpec.getSize(widthMeasureSpec);

        setMeasuredDimension(widthSize, heightSize);
        mHeightMeasureSpec = heightMeasureSpec;
        mWidthMeasureSpec = widthMeasureSpec;
    }

    int getChildHeight(View child) { return child.getMeasuredHeight(); }
    int getChildWidth(View child) { return child.getMeasuredWidth(); }

    /** AOSP AbsSpinner.recycleAllViews (:265-276): all views go in recycler. */
    void recycleAllViews() {
        final int childCount = getChildCount();
        final RecycleBin recycleBin = mRecycler;
        final int position = mFirstPosition;
        for (int i = 0; i < childCount; i++) {
            final View v = getChildAt(i);
            final int index = position + i;
            recycleBin.put(index, v);
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (MACAPK_SPINNER_RECYCLE) {
            // AOSP Spinner.onLayout (:631-636) / Gallery.onLayout: the same
            // three lines in both AOSP subclasses.
            mInLayout = true;
            layout(0, false);
            mInLayout = false;
            return;
        }
        removeAllViewsInLayout();
        View sel = getSelectedView();
        if (sel == null) return;
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        sel.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int h = sel.getMeasuredHeight();
        if (sel.getLayoutParams() == null) {
            sel.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        addViewInLayout(sel, 0, sel.getLayoutParams());
        sel.layout(getPaddingLeft(), getPaddingTop(),
                getPaddingLeft() + w, getPaddingTop() + h);
    }

    /** The horizontal gravity layout(int, boolean) places the selected child
     *  with. Spinner answers its own mGravity (AOSP Spinner.layout :680-689);
     *  Gallery's AOSP layout centres the selected child
     *  (`childrenLeft + (childrenWidth / 2) - (sel.getWidth() / 2)`), which is
     *  this default. */
    int macapkSelectionGravity() { return android.view.Gravity.CENTER_HORIZONTAL; }

    /** AOSP Spinner.layout(int, boolean) (:645-698). AOSP declares it abstract
     *  in AbsSpinner (:314) and each subclass implements it; ours has one
     *  concrete body shared by Spinner and Gallery (whose carousel is not
     *  painted -- see Gallery.java's header), selected-child only.
     *  Package-private, as in AOSP, so an app subclass cannot see it. */
    void layout(int delta, boolean animate) {
        final int childrenLeft = mSpinnerPadding.left;
        final int childrenWidth = getRight() - getLeft() - mSpinnerPadding.left - mSpinnerPadding.right;

        if (mDataChanged) {
            handleDataChanged();
        }

        // Handle the empty set by removing all views
        if (mItemCount == 0) {
            resetList();
            return;
        }

        if (mNextSelectedPosition >= 0) {
            setSelectedPositionInt(mNextSelectedPosition);
        }

        recycleAllViews();

        // Clear out old views
        removeAllViewsInLayout();

        // Make selected view and position it
        mFirstPosition = mSelectedPosition;

        if (mAdapter != null) {
            final View sel = makeView(mSelectedPosition, true);
            final int width = sel.getMeasuredWidth();
            int selectedOffset = childrenLeft;
            final int absoluteGravity = android.view.Gravity.getAbsoluteGravity(
                    macapkSelectionGravity(), getLayoutDirection());
            switch (absoluteGravity & android.view.Gravity.HORIZONTAL_GRAVITY_MASK) {
                case android.view.Gravity.CENTER_HORIZONTAL:
                    selectedOffset = childrenLeft + (childrenWidth / 2) - (width / 2);
                    break;
                case android.view.Gravity.RIGHT:
                    selectedOffset = childrenLeft + childrenWidth - width;
                    break;
            }
            sel.offsetLeftAndRight(selectedOffset);
        }

        // Flush any cached views that did not get reused above
        mRecycler.clear();

        invalidate();

        checkSelectionChanged();

        mDataChanged = false;
        mNeedSync = false;
        setNextSelectedPositionInt(mSelectedPosition);
    }

    /** AOSP Spinner.makeView (:710-727): the recycler first (unless the data
     *  changed, when the old view may show stale content), else the adapter. */
    View makeView(int position, boolean addChild) {
        View child;
        if (!mDataChanged) {
            child = mRecycler.get(position);
            if (child != null) {
                // Position the view
                setUpChild(child, addChild);
                return child;
            }
        }
        // Nothing found in the recycler -- ask the adapter for a view
        child = mAdapter.getView(position, null, this);
        // Position the view
        setUpChild(child, addChild);
        return child;
    }

    /** AOSP Spinner.setUpChild (:739-783), without the
     *  disableChildrenWhenDisabled attribute, which this port does not read. */
    private void setUpChild(View child, boolean addChild) {
        // Respect layout params that are already in the view. Otherwise
        // make some up...
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp == null) {
            lp = generateDefaultLayoutParams();
        }
        addViewInLayout(child, 0, lp);
        child.setSelected(hasFocus());

        // Get measure specs
        final int childHeightSpec = ViewGroup.getChildMeasureSpec(mHeightMeasureSpec,
                mSpinnerPadding.top + mSpinnerPadding.bottom, lp.height);
        final int childWidthSpec = ViewGroup.getChildMeasureSpec(mWidthMeasureSpec,
                mSpinnerPadding.left + mSpinnerPadding.right, lp.width);

        // Measure child
        child.measure(childWidthSpec, childHeightSpec);

        // Position vertically based on gravity setting
        final int childTop = mSpinnerPadding.top
                + ((getMeasuredHeight() - mSpinnerPadding.bottom
                        - mSpinnerPadding.top - child.getMeasuredHeight()) / 2);
        final int childBottom = childTop + child.getMeasuredHeight();
        final int width = child.getMeasuredWidth();
        final int childLeft = 0;
        final int childRight = childLeft + width;
        child.layout(childLeft, childTop, childRight, childBottom);

        if (!addChild) {
            removeViewInLayout(child);
        }
    }

    // ---- AOSP AdapterView selection bookkeeping (android14-release
    // AdapterView.java), ported here because our AdapterView does not carry it.

    /** AdapterView.getItemIdAtPosition's guard: no id for a negative position. */
    private long macapkItemId(int position) {
        final SpinnerAdapter a = mAdapter;
        return (a == null || position < 0) ? INVALID_ROW_ID : a.getItemId(position);
    }

    /** AdapterView.setSelectedPositionInt. */
    void setSelectedPositionInt(int position) {
        mSelectedPosition = position;
        mSelectedRowId = macapkItemId(position);
    }

    /** AdapterView.setNextSelectedPositionInt. Also mirrors the position into
     *  our AdapterView's `selectedPosition`, which getSelectedItem() /
     *  getSelectedItemId() read (AOSP reads mNextSelectedPosition there). */
    void setNextSelectedPositionInt(int position) {
        mNextSelectedPosition = position;
        mNextSelectedRowId = macapkItemId(position);
        selectedPosition = position;
        // If we are trying to sync to the selection, update that too
        if (mNeedSync && mSyncMode == SYNC_SELECTED_POSITION && position >= 0) {
            mSyncPosition = position;
            mSyncRowId = mNextSelectedRowId;
        }
    }

    /** AdapterView.rememberSyncState, for the one child a spinner shows. */
    void rememberSyncState() {
        if (getChildCount() > 0) {
            mNeedSync = true;
            if (mSelectedPosition >= 0) {
                // Sync the selection state
                mSyncRowId = mNextSelectedRowId;
                mSyncPosition = mNextSelectedPosition;
                mSyncMode = SYNC_SELECTED_POSITION;
            } else {
                // Sync the based on the offset of the first view
                final SpinnerAdapter a = mAdapter;
                if (a != null && mFirstPosition >= 0 && mFirstPosition < a.getCount()) {
                    mSyncRowId = a.getItemId(mFirstPosition);
                } else {
                    mSyncRowId = NO_ID;
                }
                mSyncPosition = mFirstPosition;
                mSyncMode = SYNC_FIRST_POSITION;
            }
        }
    }

    /** AdapterView.findSyncPosition: search outward from the old position for
     *  the row with the old selection's id, within SYNC_MAX_DURATION_MILLIS. */
    int findSyncPosition() {
        final int count = mItemCount;
        if (count == 0) return INVALID_POSITION;
        final long idToMatch = mSyncRowId;
        int seed = mSyncPosition;
        // If there isn't a selection don't hunt for it
        if (idToMatch == INVALID_ROW_ID) return INVALID_POSITION;
        // Pin seed to reasonable values
        seed = Math.max(0, seed);
        seed = Math.min(count - 1, seed);
        final long endTime = android.os.SystemClock.uptimeMillis() + SYNC_MAX_DURATION_MILLIS;
        int first = seed;
        int last = seed;
        boolean next = false;
        boolean hitFirst;
        boolean hitLast;
        final SpinnerAdapter adapter = mAdapter;
        if (adapter == null) return INVALID_POSITION;
        while (android.os.SystemClock.uptimeMillis() <= endTime) {
            final long rowId = adapter.getItemId(seed);
            if (rowId == idToMatch) return seed;   // Found it!
            hitLast = last == count - 1;
            hitFirst = first == 0;
            if (hitLast && hitFirst) break;        // Looked at everything
            if (hitFirst || (next && !hitLast)) {
                last++;
                seed = last;
                next = false;
            } else if (hitLast || (!next && !hitFirst)) {
                first--;
                seed = first;
                next = true;
            }
        }
        return INVALID_POSITION;
    }

    /** AdapterView.handleDataChanged. lookForSelectablePosition is AdapterView's
     *  default (`return position;`), so it is folded in. */
    void handleDataChanged() {
        final int count = mItemCount;
        boolean found = false;
        if (count > 0) {
            int newPos;
            // Find the row we are supposed to sync to
            if (mNeedSync) {
                // Update this first, since setNextSelectedPositionInt inspects it
                mNeedSync = false;
                // See if we can find a position in the new data with the same
                // id as the old selection
                newPos = findSyncPosition();
                if (newPos >= 0) {
                    // Same row id is selected
                    mSyncPosition = newPos;
                    // Restore selection
                    setNextSelectedPositionInt(newPos);
                    found = true;
                }
            }
            if (!found) {
                // Try to use the same position if we can't find matching data
                newPos = getSelectedItemPosition();
                // Pin position to the available range
                if (newPos >= count) newPos = count - 1;
                if (newPos < 0) newPos = 0;
                setNextSelectedPositionInt(newPos);
                checkSelectionChanged();
                found = true;
            }
        }
        if (!found) {
            // Nothing is selected
            mSelectedPosition = INVALID_POSITION;
            mSelectedRowId = INVALID_ROW_ID;
            mNextSelectedPosition = INVALID_POSITION;
            mNextSelectedRowId = INVALID_ROW_ID;
            selectedPosition = INVALID_POSITION;
            mNeedSync = false;
            checkSelectionChanged();
        }
    }

    /** AdapterView.checkSelectionChanged. Under MACAPK_SPINNER_SELECT_NOTIFY=0
     *  the old position still tracks (setSelectionInt compares against it) but
     *  nothing fires: the legacy callback runs from setSelection instead. */
    void checkSelectionChanged() {
        if (!MACAPK_SPINNER_SELECT_NOTIFY) {
            mOldSelectedPosition = mSelectedPosition;
            mOldSelectedRowId = mSelectedRowId;
            return;
        }
        if ((mSelectedPosition != mOldSelectedPosition) || (mSelectedRowId != mOldSelectedRowId)) {
            selectionChanged();
            mOldSelectedPosition = mSelectedPosition;
            mOldSelectedRowId = mSelectedRowId;
        }
        // If we have a pending selection notification -- and we won't if we
        // just fired one in selectionChanged() -- run it now.
        if (mPendingSelectionNotifier != null) {
            mPendingSelectionNotifier.run();
        }
    }

    /** AdapterView.selectionChanged, minus the accessibility and autofill
     *  notifications this port has no consumer for. */
    void selectionChanged() {
        // We're about to post or run the selection notifier, so we don't need
        // a pending notifier.
        mPendingSelectionNotifier = null;
        if (onItemSelectedListener != null) {
            if (mInLayout || mBlockLayoutRequests) {
                // If we are in a layout traversal, defer notification
                // by posting. This ensures that the view tree is
                // in a consistent state and is able to accommodate
                // new layout or invalidate requests.
                if (mSelectionNotifier == null) {
                    mSelectionNotifier = new SelectionNotifier();
                } else {
                    removeCallbacks(mSelectionNotifier);
                }
                post(mSelectionNotifier);
            } else {
                dispatchOnItemSelected();
            }
        }
    }

    /** AdapterView.SelectionNotifier. AOSP defers when the data changed AND the
     *  ViewRootImpl still has a layout pending; our stand-in for the latter is
     *  "attached and this view's own layout is still requested" (the data-set
     *  observer requested it), so a detached spinner dispatches at once, as it
     *  does on AOSP where getViewRootImpl() is null. */
    private class SelectionNotifier implements Runnable {
        @Override public void run() {
            mPendingSelectionNotifier = null;
            if (mDataChanged && isAttachedToWindow() && isLayoutRequested()) {
                // Data has changed between when this SelectionNotifier was
                // posted and now. Postpone the notification until the next
                // layout is complete and we run checkSelectionChanged().
                if (getAdapter() != null) {
                    mPendingSelectionNotifier = this;
                }
            } else {
                dispatchOnItemSelected();
            }
        }
    }

    /** AdapterView.dispatchOnItemSelected -> fireOnSelected. */
    private void dispatchOnItemSelected() {
        final OnItemSelectedListener l = onItemSelectedListener;
        if (l == null) return;
        final int selection = getSelectedItemPosition();
        if (selection >= 0) {
            final View v = getSelectedView();
            l.onItemSelected(this, v, selection, getAdapter().getItemId(selection));
        } else {
            l.onNothingSelected(this);
        }
    }

    /** AOSP AbsSpinner.pointToPosition (:356-375): the child under (x, y). The
     *  legacy answer was a constant INVALID_POSITION. */
    public int pointToPosition(int x, int y) {
        if (!MACAPK_SPINNER_RECYCLE) return INVALID_POSITION;
        android.graphics.Rect frame = mTouchFrame;
        if (frame == null) {
            mTouchFrame = new android.graphics.Rect();
            frame = mTouchFrame;
        }
        final int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                child.getHitRect(frame);
                if (frame.contains(x, y)) {
                    return mFirstPosition + i;
                }
            }
        }
        return INVALID_POSITION;
    }

    /** AOSP AbsSpinner.RecycleBin (:460-496). get() REMOVES the entry: that is
     *  what makes a second measure in the same traversal take the SAME view
     *  back instead of inflating again. */
    class RecycleBin {
        private final android.util.SparseArray<View> mScrapHeap = new android.util.SparseArray<View>();

        public void put(int position, View v) {
            mScrapHeap.put(position, v);
        }

        View get(int position) {
            final View result = mScrapHeap.get(position);
            if (result != null) {
                mScrapHeap.delete(position);
            }
            return result;
        }

        void clear() {
            final android.util.SparseArray<View> scrapHeap = mScrapHeap;
            final int count = scrapHeap.size();
            for (int i = 0; i < count; i++) {
                final View view = scrapHeap.valueAt(i);
                if (view != null) {
                    removeDetachedView(view, true);
                }
            }
            scrapHeap.clear();
        }
    }
}
