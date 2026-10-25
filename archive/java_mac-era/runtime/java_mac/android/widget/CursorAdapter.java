// android.widget.CursorAdapter -- abstract BaseAdapter that exposes a
// Cursor's rows to an AdapterView. Subclasses implement newView/bindView.
// Was a bare `extends Object` stub; SimpleCursorAdapter and many app
// adapters extend it and rely on swapCursor/changeCursor/getCursor and the
// row-position cursor movement. AOSP:
//   frameworks/base/core/java/android/widget/CursorAdapter.java
//   public abstract class CursorAdapter extends BaseAdapter
//       implements Filterable, ThemedSpinnerAdapter, CursorFilter.CursorFilterClient
package android.widget;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;

public abstract class CursorAdapter extends BaseAdapter
        implements Filterable, ThemedSpinnerAdapter {

    public static final int FLAG_AUTO_REQUERY = 0x01;
    public static final int FLAG_REGISTER_CONTENT_OBSERVER = 0x02;

    protected boolean mDataValid;
    protected boolean mAutoRequery;
    protected Cursor mCursor;
    protected Context mContext;
    protected int mRowIDColumn;
    protected DataSetObserver mDataSetObserver;
    protected FilterQueryProvider mFilterQueryProvider;
    private Resources.Theme mDropDownViewTheme;
    private Filter mFilter;

    public CursorAdapter(Context context, Cursor c) {
        init(context, c, FLAG_AUTO_REQUERY);
    }

    public CursorAdapter(Context context, Cursor c, boolean autoRequery) {
        init(context, c, autoRequery ? FLAG_AUTO_REQUERY : 0);
    }

    public CursorAdapter(Context context, Cursor c, int flags) {
        init(context, c, flags);
    }

    protected void init(Context context, Cursor c, int flags) {
        mAutoRequery = (flags & FLAG_AUTO_REQUERY) == FLAG_AUTO_REQUERY;
        boolean cursorPresent = c != null;
        mCursor = c;
        mDataValid = cursorPresent;
        mContext = context;
        mRowIDColumn = cursorPresent ? c.getColumnIndexOrThrow("_id") : -1;
    }

    public Cursor getCursor() { return mCursor; }

    @Override public int getCount() {
        if (mDataValid && mCursor != null) return mCursor.getCount();
        return 0;
    }

    @Override public Object getItem(int position) {
        if (mDataValid && mCursor != null) {
            mCursor.moveToPosition(position);
            return mCursor;
        }
        return null;
    }

    @Override public long getItemId(int position) {
        if (mDataValid && mCursor != null) {
            if (mCursor.moveToPosition(position)) {
                return mCursor.getLong(mRowIDColumn);
            }
        }
        return 0;
    }

    @Override public boolean hasStableIds() { return true; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        if (!mDataValid) {
            throw new IllegalStateException(
                    "this should only be called when the cursor is valid");
        }
        if (!mCursor.moveToPosition(position)) {
            throw new IllegalStateException(
                    "couldn't move cursor to position " + position);
        }
        View v = (convertView == null)
                ? newView(mContext, mCursor, parent) : convertView;
        bindView(v, mContext, mCursor);
        return v;
    }

    @Override public View getDropDownView(int position, View convertView,
            ViewGroup parent) {
        if (mDataValid) {
            mCursor.moveToPosition(position);
            View v = (convertView == null)
                    ? newDropDownView(mContext, mCursor, parent) : convertView;
            bindView(v, mContext, mCursor);
            return v;
        }
        return null;
    }

    public abstract View newView(Context context, Cursor cursor, ViewGroup parent);

    public View newDropDownView(Context context, Cursor cursor, ViewGroup parent) {
        return newView(context, cursor, parent);
    }

    public abstract void bindView(View view, Context context, Cursor cursor);

    public void changeCursor(Cursor cursor) {
        Cursor old = swapCursor(cursor);
        if (old != null) old.close();
    }

    public Cursor swapCursor(Cursor newCursor) {
        if (newCursor == mCursor) return null;
        Cursor oldCursor = mCursor;
        mCursor = newCursor;
        if (newCursor != null) {
            mRowIDColumn = newCursor.getColumnIndexOrThrow("_id");
            mDataValid = true;
            notifyDataSetChanged();
        } else {
            mRowIDColumn = -1;
            mDataValid = false;
            notifyDataSetInvalidated();
        }
        return oldCursor;
    }

    public CharSequence convertToString(Cursor cursor) {
        return cursor == null ? "" : cursor.toString();
    }

    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        if (mFilterQueryProvider != null) {
            return mFilterQueryProvider.runQuery(constraint);
        }
        return mCursor;
    }

    @Override public Filter getFilter() {
        if (mFilter == null) mFilter = new CursorFilter();
        return mFilter;
    }

    public FilterQueryProvider getFilterQueryProvider() {
        return mFilterQueryProvider;
    }

    public void setFilterQueryProvider(FilterQueryProvider filterQueryProvider) {
        mFilterQueryProvider = filterQueryProvider;
    }

    @Override public void setDropDownViewTheme(Resources.Theme theme) {
        mDropDownViewTheme = theme;
    }

    @Override public Resources.Theme getDropDownViewTheme() {
        return mDropDownViewTheme;
    }

    // AOSP uses a package-private CursorFilter helper that drives
    // runQueryOnBackgroundThread + changeCursor. Inlined here.
    private class CursorFilter extends Filter {
        @Override public CharSequence convertResultToString(Object resultValue) {
            return convertToString((Cursor) resultValue);
        }

        @Override protected FilterResults performFiltering(CharSequence constraint) {
            Cursor cursor = runQueryOnBackgroundThread(constraint);
            FilterResults results = new FilterResults();
            if (cursor != null) {
                results.count = cursor.getCount();
                results.values = cursor;
            } else {
                results.count = 0;
                results.values = null;
            }
            return results;
        }

        @Override protected void publishResults(CharSequence constraint,
                FilterResults results) {
            Cursor oldCursor = getCursor();
            if (results.values != null && results.values != oldCursor) {
                changeCursor((Cursor) results.values);
            }
        }
    }

    /** AOSP CursorAdapter.java:488 -- protected; the ContentObserver on the
     *  cursor calls it when the underlying content changes, and with
     *  auto-requery on it re-runs the query. mAutoRequery/mCursor/mDataValid
     *  are this class's own fields. */
    protected void onContentChanged() {
        if (mAutoRequery && mCursor != null && !mCursor.isClosed()) {
            mDataValid = mCursor.requery();
        }
    }
}
