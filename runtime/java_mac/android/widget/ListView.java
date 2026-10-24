// android.widget.ListView -- AdapterView for vertically scrolling rows.
// Now backed by AbsListView's real recycling layout (drag-scroll + row
// reuse + item clicks). Header/footer views are folded into the adapter via
// HeaderViewListAdapter exactly as AOSP does, so getCount/positions account
// for them. Dividers are painted in dispatchDraw and reserved by the fill
// loop (see macapkDividerHeight); they were state-only until v1.0.1036+.
// AOSP: frameworks/base/core/java/android/widget/ListView.java
//   public class ListView extends AbsListView
package android.widget;

import android.content.Context;
import android.view.View;

import java.util.ArrayList;

public class ListView extends AbsListView {
    private final ArrayList<HeaderViewListAdapter.FixedViewInfo> mHeaderViewInfos =
            new ArrayList<HeaderViewListAdapter.FixedViewInfo>();
    private final ArrayList<HeaderViewListAdapter.FixedViewInfo> mFooterViewInfos =
            new ArrayList<HeaderViewListAdapter.FixedViewInfo>();

    private android.graphics.drawable.Drawable mDivider;
    private int mDividerHeight;
    private boolean mHeaderDividersEnabled = true;
    private boolean mFooterDividersEnabled = true;
    private boolean mItemsCanFocus;

    private ListAdapter mUserAdapter;

    public ListView(Context c) { super(c); }
    public ListView(Context c, android.util.AttributeSet a) { super(c, a); }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public ListView(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public ListView(Context c, android.util.AttributeSet a, int s) { super(c, a, s); }

    @Override public void setAdapter(ListAdapter adapter) {
        mUserAdapter = adapter;
        if (mHeaderViewInfos.isEmpty() && mFooterViewInfos.isEmpty()) {
            super.setAdapter(adapter);
        } else {
            super.setAdapter(new HeaderViewListAdapter(
                    mHeaderViewInfos, mFooterViewInfos, adapter));
        }
    }

    private void rewrapAdapter() {
        if (mHeaderViewInfos.isEmpty() && mFooterViewInfos.isEmpty()) {
            super.setAdapter(mUserAdapter);
        } else {
            super.setAdapter(new HeaderViewListAdapter(
                    mHeaderViewInfos, mFooterViewInfos, mUserAdapter));
        }
    }

    public void addHeaderView(View v) { addHeaderView(v, null, true); }
    public void addHeaderView(View v, Object data, boolean isSelectable) {
        HeaderViewListAdapter.FixedViewInfo info =
                new HeaderViewListAdapter.FixedViewInfo();
        info.view = v; info.data = data; info.isSelectable = isSelectable;
        mHeaderViewInfos.add(info);
        rewrapAdapter();
    }
    public boolean removeHeaderView(View v) {
        for (int i = 0; i < mHeaderViewInfos.size(); i++) {
            if (mHeaderViewInfos.get(i).view == v) {
                mHeaderViewInfos.remove(i);
                rewrapAdapter();
                return true;
            }
        }
        return false;
    }
    public int getHeaderViewsCount() { return mHeaderViewInfos.size(); }

    /** AOSP ListView.findViewTraversal: after the children, look in the header
     *  and footer views, which are adapter items rather than children until the
     *  list lays them out (and again once they scroll off). Without it a
     *  findViewById for a view inside a header added in onCreate came back
     *  null: WhatsApp's contact screen requireViewById'd its media card stub
     *  there, the throw ended its onCreate early, and its back arrow later hit
     *  the ViewModel that onCreate never created. */
    @Override protected java.util.List<android.view.View> macapkUnattachedFindRoots() {
        if (mHeaderViewInfos.isEmpty() && mFooterViewInfos.isEmpty()) return null;
        java.util.ArrayList<android.view.View> out =
            new java.util.ArrayList<>(mHeaderViewInfos.size() + mFooterViewInfos.size());
        for (HeaderViewListAdapter.FixedViewInfo i : mHeaderViewInfos) out.add(i.view);
        for (HeaderViewListAdapter.FixedViewInfo i : mFooterViewInfos) out.add(i.view);
        return out;
    }

    public void addFooterView(View v) { addFooterView(v, null, true); }
    public void addFooterView(View v, Object data, boolean isSelectable) {
        HeaderViewListAdapter.FixedViewInfo info =
                new HeaderViewListAdapter.FixedViewInfo();
        info.view = v; info.data = data; info.isSelectable = isSelectable;
        mFooterViewInfos.add(info);
        rewrapAdapter();
    }
    public boolean removeFooterView(View v) {
        for (int i = 0; i < mFooterViewInfos.size(); i++) {
            if (mFooterViewInfos.get(i).view == v) {
                mFooterViewInfos.remove(i);
                rewrapAdapter();
                return true;
            }
        }
        return false;
    }
    public int getFooterViewsCount() { return mFooterViewInfos.size(); }

    public void setDivider(android.graphics.drawable.Drawable divider) {
        mDivider = divider;
        mDividerHeight = divider != null ? divider.getIntrinsicHeight() : 0;
    }
    public android.graphics.drawable.Drawable getDivider() { return mDivider; }
    public void setDividerHeight(int height) { mDividerHeight = height; }
    public int getDividerHeight() { return mDividerHeight; }
    public void setHeaderDividersEnabled(boolean e) { mHeaderDividersEnabled = e; }
    public boolean areHeaderDividersEnabled() { return mHeaderDividersEnabled; }
    public void setFooterDividersEnabled(boolean e) { mFooterDividersEnabled = e; }
    public boolean areFooterDividersEnabled() { return mFooterDividersEnabled; }

    // AOSP ListView.setItemsCanFocus (frameworks/base/.../widget/ListView.java):
    // indicates that views the ListAdapter creates can themselves contain
    // focusable children (e.g. a row with an embedded Button or EditText).
    // The real, observable side effect is the descendant-focusability flip:
    // turning it OFF blocks the row's descendants from ever taking focus so
    // the row itself becomes the focusable unit, exactly as AOSP does it.
    // (ViewGroup.setDescendantFocusability is currently a no-op elsewhere in
    // this tree -- android/view/ViewGroup.java, out of this lane's scope --
    // so the flip has no observable effect yet, but mirroring the real AOSP
    // call keeps this correct the moment that gap closes.)
    public void setItemsCanFocus(boolean itemsCanFocus) {
        mItemsCanFocus = itemsCanFocus;
        if (!itemsCanFocus) {
            setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
    }
    public boolean getItemsCanFocus() { return mItemsCanFocus; }

    public void setSelectionAfterHeaderView() { setSelection(mHeaderViewInfos.size()); }

    /** AOSP ListView.fillDown/fillUp advance by `mDividerHeight` between rows;
     *  our fill loop lives in AbsListView, which asks for the number through
     *  this hook. Paint and reservation MUST agree -- a divider that draws but
     *  is not reserved overlaps the following row. */
    @Override protected int macapkDividerHeight() {
        return (mDivider != null && mDividerHeight > 0) ? mDividerHeight : 0;
    }

    /** AOSP ListView.onMeasure (ListView.java:1319-1322, master):
     *
     *      if (heightMode == MeasureSpec.AT_MOST) {
     *          heightSize = measureHeightOfChildren(widthMeasureSpec, 0,
     *                  NO_POSITION, heightSize, -1);
     *      }
     *
     *  A wrap_content list is as tall as its rows (capped at the space offered),
     *  not as tall as the space offered. AbsListView.onMeasure answered the
     *  whole AT_MOST size ("a list fills the space it is given"), so a list in a
     *  wrap_content parent ate every remaining pixel: measured on our
     *  AlertDialog, a ONE-row multi-choice list grew the dialog to the full
     *  1796px surface and its button bar was measured at 0px -- buttons
     *  clipped, dialog undismissable. EXACTLY and UNSPECIFIED are untouched. */
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            setMeasuredDimension(getMeasuredWidth(), macapkMeasureHeightOfChildren(
                    MeasureSpec.getSize(widthMeasureSpec),
                    MeasureSpec.getSize(heightMeasureSpec),
                    macapkDividerHeight()));
        }
    }

    /** AOSP ListView.dispatchDraw (frameworks/base core/java/android/widget/
     *  ListView.java, drawDividers): after the children are drawn, a divider is
     *  painted in the gap BELOW each child, honouring
     *  areHeaderDividersEnabled/areFooterDividersEnabled.
     *
     *  ⚠ mDivider and mDividerHeight were stored by the setters and read back by
     *  the getters and referenced NOWHERE else in this class, so every
     *  `android:divider` in every list in every app was invisible.
     *
     *  Simplification: AOSP also paints an overscroll divider and the divider
     *  above the first item when the list is scrolled. This paints only the gaps
     *  the fill loop actually reserved, so paint and layout cannot disagree. */
    @Override protected void dispatchDraw(android.graphics.Canvas canvas) {
        super.dispatchDraw(canvas);
        final android.graphics.drawable.Drawable divider = mDivider;
        final int dividerHeight = mDividerHeight;
        if (divider == null || dividerHeight <= 0) return;
        final int count = getChildCount();
        if (count < 2) return;
        final int left = getPaddingLeft();
        final int right = getWidth() - getPaddingRight();
        if (right <= left) return;

        final int headerCount = mHeaderViewInfos.size();
        final int itemCount = getCount();
        final int footerStart = itemCount - mFooterViewInfos.size();
        final int first = getFirstVisiblePosition();

        for (int i = 0; i < count - 1; i++) {
            final int pos = first + i;
            // AOSP: a divider immediately after the last header is suppressed
            // when header dividers are off, and the one immediately before the
            // first footer when footer dividers are off.
            if (!mHeaderDividersEnabled && pos < headerCount) continue;
            if (!mFooterDividersEnabled && (pos + 1) >= footerStart) continue;
            final View child = getChildAt(i);
            if (child == null) continue;
            final int top = child.getBottom();
            divider.setBounds(left, top, right, top + dividerHeight);
            try { divider.draw(canvas); } catch (Throwable ignored) { }
        }
    }

    // NOTE: ListView used to redeclare `getPositionForView(View) { return -1; }`
    // here, SHADOWING AdapterView's method -- so fixing AdapterView alone would
    // not have fixed ListView, which is where nearly every caller lives. The
    // inherited AOSP implementation is used now; do not reintroduce an override.

    // ==================================================================
    // API-36 surface completion (apidiff: android.widget.ListView had 7 public
    // members missing). Bodies mirror
    // frameworks/base/core/java/android/widget/ListView.java (android16-release).
    // ==================================================================

    private android.graphics.drawable.Drawable mOverScrollHeader;
    private android.graphics.drawable.Drawable mOverScrollFooter;

    /** AOSP :159/:277 -- the largest arrow-key scroll, a third of the viewport. */
    private static final float MACAPK_MAX_SCROLL_FACTOR = 0.33f;
    public int getMaxScrollAmount() {
        // AOSP computes (mBottom - mTop), which is exactly getHeight().
        return (int) (MACAPK_MAX_SCROLL_FACTOR * getHeight());
    }

    /** AOSP :3742 / :3752 / :3763 / :3771 -- the drawables painted above and
     *  below all list content when the list is overscrolled or under-filled.
     *  This port paints neither (no overscroll surface), so they are state
     *  only; AOSP's own invalidate-on-set is kept. */
    public void setOverscrollHeader(android.graphics.drawable.Drawable header) {
        mOverScrollHeader = header;
        invalidate();
    }
    public android.graphics.drawable.Drawable getOverscrollHeader() { return mOverScrollHeader; }
    public void setOverscrollFooter(android.graphics.drawable.Drawable footer) {
        mOverScrollFooter = footer;
        invalidate();
    }
    public android.graphics.drawable.Drawable getOverscrollFooter() { return mOverScrollFooter; }

    /** AOSP :3993 -- @Deprecated. Prefers the stable-id path
     *  (AbsListView.getCheckedItemIds) and otherwise reproduces the old,
     *  AOSP-acknowledged-buggy behaviour verbatim for legacy apps: walk the
     *  checked-state array, keep the ids of the TRUE entries, and trim. */
    @Deprecated
    public long[] getCheckItemIds() {
        if (mAdapter != null && mAdapter.hasStableIds()) {
            return getCheckedItemIds();
        }
        if (getChoiceMode() != CHOICE_MODE_NONE && mAdapter != null) {
            final android.util.SparseBooleanArray states = getCheckedItemPositions();
            final int count = states.size();
            final long[] ids = new long[count];
            int checkedCount = 0;
            for (int i = 0; i < count; i++) {
                if (states.valueAt(i)) {
                    ids[checkedCount++] = mAdapter.getItemId(states.keyAt(i));
                }
            }
            if (checkedCount == count) return ids;
            final long[] result = new long[checkedCount];
            System.arraycopy(ids, 0, result, 0, checkedCount);
            return result;
        }
        return new long[0];
    }

    /** AOSP :1026 -- a public override of AbsListView's package-private form. */
    @Override
    public void smoothScrollByOffset(int offset) { super.smoothScrollByOffset(offset); }
}
