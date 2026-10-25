// android.widget.AdapterView -- common parent for ListView/Spinner/etc.
// We don't render rows, so most of the surface is no-op getters/setters
// that just remember what the app handed us. The two listener inner
// interfaces (OnItemClickListener and OnItemSelectedListener) are
// referenced by apps directly even when no list ever materializes.
package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

public abstract class AdapterView<T extends Adapter> extends ViewGroup {
    public static final int INVALID_POSITION  = -1;
    public static final long INVALID_ROW_ID   = Long.MIN_VALUE;
    public static final int ITEM_VIEW_TYPE_IGNORE = -1;
    public static final int ITEM_VIEW_TYPE_HEADER_OR_FOOTER = -2;

    protected T adapter;
    protected OnItemClickListener onItemClickListener;
    protected OnItemSelectedListener onItemSelectedListener;
    protected OnItemLongClickListener onItemLongClickListener;
    protected int selectedPosition = INVALID_POSITION;
    private View mEmptyView;

    public AdapterView(Context c) { super(c); }
    public AdapterView(Context c, android.util.AttributeSet a) { super(c, a); }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public AdapterView(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public AdapterView(Context c, android.util.AttributeSet a, int s) { super(c, a, s); }

    public abstract T getAdapter();
    public abstract void setAdapter(T adapter);

    public int  getCount()              { return adapter != null ? adapter.getCount() : 0; }
    public int  getSelectedItemPosition() { return selectedPosition; }
    public long getSelectedItemId()     {
        return (adapter != null && selectedPosition >= 0 && selectedPosition < adapter.getCount())
                ? adapter.getItemId(selectedPosition) : INVALID_ROW_ID;
    }
    public Object getSelectedItem()     {
        return (adapter != null && selectedPosition >= 0 && selectedPosition < adapter.getCount())
                ? adapter.getItem(selectedPosition) : null;
    }
    public Object getItemAtPosition(int position) {
        return adapter != null ? adapter.getItem(position) : null;
    }
    public long getItemIdAtPosition(int position) {
        return adapter != null ? adapter.getItemId(position) : INVALID_ROW_ID;
    }
    public View getSelectedView()       { return null; }

    public void setSelection(int position) { this.selectedPosition = position; }

    /** AOSP AdapterView.setEmptyView: the view shown in place of this one
     *  whenever the adapter has zero rows. Mirrors
     *  frameworks/base/core/java/android/widget/AdapterView.java. */
    public void setEmptyView(View emptyView) {
        mEmptyView = emptyView;
        // AOSP: an app-supplied empty-state view is content, not chrome;
        // promote it out of IMPORTANT_FOR_ACCESSIBILITY_AUTO so a screen
        // reader announces it (same idiom as View.setStateDescription).
        if (emptyView != null
                && emptyView.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            emptyView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        final T a = getAdapter();
        final boolean empty = (a == null) || a.isEmpty();
        updateEmptyStatus(empty);
    }

    public View getEmptyView() { return mEmptyView; }

    /** AOSP AdapterView.isInFilterMode: subclasses that support a text
     *  filter (narrowing the list to matching rows) override this so an
     *  empty FILTERED result doesn't show the "no data" empty view. This
     *  default (never in filter mode) matches AOSP's own AdapterView-level
     *  default; no filter-mode subclass exists in this tree yet. */
    boolean isInFilterMode() { return false; }

    /** AOSP AdapterView.updateEmptyStatus: toggles mEmptyView vs this
     *  view's visibility whenever the adapter's emptiness changes. Callers:
     *  setEmptyView() above, and (once wired) setAdapter()/the adapter's
     *  DataSetObserver in subclasses such as AbsListView. Mirrors
     *  frameworks/base/core/java/android/widget/AdapterView.java. */
    void updateEmptyStatus(boolean empty) {
        if (isInFilterMode()) {
            empty = false;
        }
        if (empty) {
            if (mEmptyView != null) {
                mEmptyView.setVisibility(View.VISIBLE);
                setVisibility(View.GONE);
            } else {
                // Caller just removed the empty view; make sure we're visible.
                setVisibility(View.VISIBLE);
            }
        } else {
            if (mEmptyView != null) mEmptyView.setVisibility(View.GONE);
            setVisibility(View.VISIBLE);
        }
    }

    public OnItemClickListener getOnItemClickListener() { return onItemClickListener; }
    public void setOnItemClickListener(OnItemClickListener l) { this.onItemClickListener = l; }
    public OnItemSelectedListener getOnItemSelectedListener() { return onItemSelectedListener; }
    public void setOnItemSelectedListener(OnItemSelectedListener l) { this.onItemSelectedListener = l; }
    public OnItemLongClickListener getOnItemLongClickListener() { return onItemLongClickListener; }
    /** AOSP AdapterView.setOnItemLongClickListener (android14 :371-376) makes the
     *  ADAPTER VIEW long-clickable, which is what arms AbsListView's
     *  CheckForLongPress. Gated with AbsListView.ITEMCLICK_AOSP, whose =0 arm
     *  made every ROW long-clickable instead. */
    public void setOnItemLongClickListener(OnItemLongClickListener l) {
        if (AbsListView.ITEMCLICK_AOSP && !isLongClickable()) setLongClickable(true);
        this.onItemLongClickListener = l;
    }

    public boolean performItemClick(View view, int position, long id) {
        if (onItemClickListener != null) {
            onItemClickListener.onItemClick(this, view, position, id);
            return true;
        }
        return false;
    }

    public interface OnItemClickListener {
        void onItemClick(AdapterView<?> parent, View view, int position, long id);
    }
    public interface OnItemLongClickListener {
        boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id);
    }
    public interface OnItemSelectedListener {
        void onItemSelected(AdapterView<?> parent, View view, int position, long id);
        void onNothingSelected(AdapterView<?> parent);
    }

    /** AOSP AdapterView declares `int mFirstPosition = 0;` -- "The position
     *  within the adapter's data set of the first view displayed" -- and the
     *  three methods below all read it. AbsListView used to declare its own
     *  copy, which SHADOWED nothing (it was the only declaration) but left this
     *  class answering constants. The field lives here now, exactly as AOSP has
     *  it, so a non-AbsListView AdapterView also gets real answers. */
    protected int mFirstPosition = 0;

    public int getFirstVisiblePosition() { return mFirstPosition; }

    public int getLastVisiblePosition() { return mFirstPosition + getChildCount() - 1; }

    /** AOSP AdapterView.getPositionForView(View): walk up from `view` until the
     *  parent IS this AdapterView, then find that ancestor among our children
     *  and return `mFirstPosition + i`.
     *
     *  ⚠ This was a hardcoded `return -1`, and ListView additionally shadowed it
     *  with a second `return -1`. `listView.getPositionForView(row)` inside a
     *  row's own OnClickListener is one of the most common ListView idioms in
     *  Android, and it always answered INVALID_POSITION -- apps then indexed
     *  arrays with -1 (IndexOutOfBoundsException) or silently acted on nothing.
     *
     *  The ClassCastException catch is AOSP's own: the walk ends at a ViewParent
     *  that is not a View (the window root), and AOSP relies on the cast to
     *  terminate it rather than testing the type. */
    public int getPositionForView(android.view.View view) {
        if (view == null) return INVALID_POSITION;
        View listItem = view;
        try {
            View v;
            while ((v = (View) listItem.getParent()) != null && !v.equals(this)) {
                listItem = v;
            }
        } catch (ClassCastException e) {
            // We made it up to the window without finding this AdapterView.
            return INVALID_POSITION;
        }
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (listItem.equals(getChildAt(i))) return mFirstPosition + i;
        }
        return INVALID_POSITION;
    }

    public static class AdapterContextMenuInfo implements android.view.ContextMenu.ContextMenuInfo {
        public android.view.View targetView;
        public int position;
        public long id;
        public AdapterContextMenuInfo(android.view.View targetView, int position, long id) {
            this.targetView = targetView; this.position = position; this.id = id;
        }
    }
}




