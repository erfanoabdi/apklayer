// android.widget.CursorTreeAdapter -- ExpandableListView adapter backed by a
// group Cursor plus a per-group child Cursor (lazily fetched via
// getChildrenCursor). Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/CursorTreeAdapter.java
//   public abstract class CursorTreeAdapter extends BaseExpandableListAdapter
//       implements Filterable, CursorFilter.CursorFilterClient
package android.widget;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.Map;

public abstract class CursorTreeAdapter extends BaseExpandableListAdapter
        implements Filterable {

    Context mContext;
    private Cursor mGroupCursor;
    private int mGroupIdColumn = -1;
    private final Map<Integer, Cursor> mChildCursors = new HashMap<Integer, Cursor>();
    private FilterQueryProvider mFilterQueryProvider;
    private Filter mFilter;

    public CursorTreeAdapter(Cursor cursor, Context context) {
        init(context, cursor, false);
    }
    public CursorTreeAdapter(Cursor cursor, Context context, boolean autoRequery) {
        init(context, cursor, autoRequery);
    }

    private void init(Context context, Cursor cursor, boolean autoRequery) {
        mContext = context;
        setGroupCursor(cursor);
    }

    public void setGroupCursor(Cursor cursor) {
        mGroupCursor = cursor;
        mGroupIdColumn = cursor != null ? cursor.getColumnIndexOrThrow("_id") : -1;
        notifyDataSetChanged();
    }

    public Cursor getCursor() { return mGroupCursor; }

    protected abstract Cursor getChildrenCursor(Cursor groupCursor);

    public Cursor getChildrenCursorSafe(int groupPosition) {
        Cursor c = mChildCursors.get(groupPosition);
        if (c == null && mGroupCursor != null
                && mGroupCursor.moveToPosition(groupPosition)) {
            c = getChildrenCursor(mGroupCursor);
            mChildCursors.put(groupPosition, c);
        }
        return c;
    }

    @Override public int getGroupCount() {
        return mGroupCursor != null ? mGroupCursor.getCount() : 0;
    }
    @Override public int getChildrenCount(int groupPosition) {
        Cursor c = getChildrenCursorSafe(groupPosition);
        return c != null ? c.getCount() : 0;
    }
    // AOSP frameworks/base core/java/android/widget/CursorTreeAdapter.java
    // (android15-release, ~lines 167/181): both are covariant overrides
    // narrowing ExpandableListAdapter's `Object getGroup/getChild(...)` to
    // `Cursor` -- CursorTreeAdapter's whole contract is that these ARE cursors,
    // and both bodies here already only ever return a Cursor or null. Widening
    // back to Object at the descriptor level (getGroup(I)Ljava/lang/Object;
    // instead of the real getGroup(I)Landroid/database/Cursor;) means guest
    // bytecode that calls the real signature (e.g. via a typed local var or a
    // direct `Cursor c = adapter.getGroup(i);` without a cast) never binds to
    // ours. Found by debug/framework_lint.py's class-shape diff against
    // android.jar.
    @Override public Cursor getGroup(int groupPosition) {
        if (mGroupCursor != null && mGroupCursor.moveToPosition(groupPosition)) {
            return mGroupCursor;
        }
        return null;
    }
    @Override public Cursor getChild(int groupPosition, int childPosition) {
        Cursor c = getChildrenCursorSafe(groupPosition);
        if (c != null && c.moveToPosition(childPosition)) return c;
        return null;
    }
    @Override public long getGroupId(int groupPosition) {
        if (mGroupCursor != null && mGroupCursor.moveToPosition(groupPosition)
                && mGroupIdColumn >= 0) {
            return mGroupCursor.getLong(mGroupIdColumn);
        }
        return 0;
    }
    @Override public long getChildId(int groupPosition, int childPosition) {
        Cursor c = getChildrenCursorSafe(groupPosition);
        if (c != null && c.moveToPosition(childPosition)) {
            int idCol = c.getColumnIndex("_id");
            if (idCol >= 0) return c.getLong(idCol);
        }
        return 0;
    }
    @Override public boolean hasStableIds() { return true; }
    @Override public boolean isChildSelectable(int groupPosition, int childPosition) { return true; }

    @Override public View getGroupView(int groupPosition, boolean isExpanded,
            View convertView, ViewGroup parent) {
        if (mGroupCursor == null || !mGroupCursor.moveToPosition(groupPosition)) {
            throw new IllegalStateException("Invalid group position " + groupPosition);
        }
        View v = convertView != null ? convertView
                : newGroupView(mContext, mGroupCursor, isExpanded, parent);
        bindGroupView(v, mContext, mGroupCursor, isExpanded);
        return v;
    }

    @Override public View getChildView(int groupPosition, int childPosition,
            boolean isLastChild, View convertView, ViewGroup parent) {
        Cursor c = getChildrenCursorSafe(groupPosition);
        if (c == null || !c.moveToPosition(childPosition)) {
            throw new IllegalStateException("Invalid child position");
        }
        View v = convertView != null ? convertView
                : newChildView(mContext, c, isLastChild, parent);
        bindChildView(v, mContext, c, isLastChild);
        return v;
    }

    protected abstract View newGroupView(Context context, Cursor cursor,
            boolean isExpanded, ViewGroup parent);
    protected abstract void bindGroupView(View view, Context context,
            Cursor cursor, boolean isExpanded);
    protected abstract View newChildView(Context context, Cursor cursor,
            boolean isLastChild, ViewGroup parent);
    protected abstract void bindChildView(View view, Context context,
            Cursor cursor, boolean isLastChild);

    public void changeCursor(Cursor cursor) {
        if (mGroupCursor != null) {
            try { mGroupCursor.close(); } catch (Throwable ignored) {}
        }
        setGroupCursor(cursor);
    }

    @Override public Filter getFilter() {
        if (mFilter == null) {
            mFilter = new Filter() {
                @Override protected FilterResults performFiltering(CharSequence constraint) {
                    Cursor c = runQueryOnBackgroundThread(constraint);
                    FilterResults r = new FilterResults();
                    r.values = c; r.count = c != null ? c.getCount() : 0;
                    return r;
                }
                @Override protected void publishResults(CharSequence constraint, FilterResults results) {
                    if (results.values != null) changeCursor((Cursor) results.values);
                }
            };
        }
        return mFilter;
    }

    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        if (mFilterQueryProvider != null) return mFilterQueryProvider.runQuery(constraint);
        return mGroupCursor;
    }
    public FilterQueryProvider getFilterQueryProvider() { return mFilterQueryProvider; }
    public void setFilterQueryProvider(FilterQueryProvider p) { mFilterQueryProvider = p; }

    public String convertToString(Cursor cursor) {
        return cursor == null ? "" : cursor.toString();
    }

    /** AOSP CursorTreeAdapter.java:308 -- notifyDataSetChanged with an explicit
     *  say over whether the per-group child cursors are released first. AOSP's
     *  releaseCursorHelpers() deactivates/closes every children cursor; the
     *  equivalent state here is mChildCursors. */
    public void notifyDataSetChanged(boolean releaseCursors) {
        if (releaseCursors) mChildCursors.clear();
        super.notifyDataSetChanged();
    }

    /** AOSP :152 -- install a children cursor for one group WITHOUT asking the
     *  subclass for it (the caller already has it, typically from an async
     *  query), and without releasing the previous one, "since we know exactly
     *  what data is changing". */
    public void setChildrenCursor(int groupPosition, Cursor childrenCursor) {
        mChildCursors.put(Integer.valueOf(groupPosition), childrenCursor);
        notifyDataSetChanged();
    }
}
