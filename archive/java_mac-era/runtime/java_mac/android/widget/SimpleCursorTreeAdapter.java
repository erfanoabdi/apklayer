// android.widget.SimpleCursorTreeAdapter -- binds group/child cursor columns
// to TextView/ImageView row children by id. Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/SimpleCursorTreeAdapter.java
//   public abstract class SimpleCursorTreeAdapter extends ResourceCursorTreeAdapter
package android.widget;

import android.content.Context;
import android.database.Cursor;
import android.view.View;

public abstract class SimpleCursorTreeAdapter extends ResourceCursorTreeAdapter {
    private int[] mGroupFrom;
    private final int[] mGroupTo;
    private int[] mChildFrom;
    private final int[] mChildTo;
    private final String[] mOriginalGroupFrom;
    private final String[] mOriginalChildFrom;

    public SimpleCursorTreeAdapter(Context context, Cursor cursor,
            int collapsedGroupLayout, int expandedGroupLayout,
            String[] groupFrom, int[] groupTo,
            int childLayout, int lastChildLayout,
            String[] childFrom, int[] childTo) {
        super(context, cursor, collapsedGroupLayout, expandedGroupLayout,
                childLayout, lastChildLayout);
        mOriginalGroupFrom = groupFrom;
        mGroupTo = groupTo;
        mOriginalChildFrom = childFrom;
        mChildTo = childTo;
        initFromColumns(cursor, groupFrom, true);
    }

    public SimpleCursorTreeAdapter(Context context, Cursor cursor,
            int collapsedGroupLayout, int expandedGroupLayout,
            String[] groupFrom, int[] groupTo,
            int childLayout, String[] childFrom, int[] childTo) {
        this(context, cursor, collapsedGroupLayout, expandedGroupLayout,
                groupFrom, groupTo, childLayout, childLayout, childFrom, childTo);
    }

    public SimpleCursorTreeAdapter(Context context, Cursor cursor,
            int groupLayout, String[] groupFrom, int[] groupTo,
            int childLayout, String[] childFrom, int[] childTo) {
        this(context, cursor, groupLayout, groupLayout, groupFrom, groupTo,
                childLayout, childLayout, childFrom, childTo);
    }

    private void initFromColumns(Cursor cursor, String[] groupFrom, boolean group) {
        if (cursor != null && groupFrom != null) {
            mGroupFrom = new int[groupFrom.length];
            for (int i = 0; i < groupFrom.length; i++) {
                mGroupFrom[i] = cursor.getColumnIndexOrThrow(groupFrom[i]);
            }
        }
    }

    @Override protected void bindGroupView(View view, Context context,
            Cursor cursor, boolean isExpanded) {
        if (mGroupFrom == null) initFromColumns(cursor, mOriginalGroupFrom, true);
        bindView(view, cursor, mGroupFrom, mGroupTo);
    }

    @Override protected void bindChildView(View view, Context context,
            Cursor cursor, boolean isLastChild) {
        if (mChildFrom == null && mOriginalChildFrom != null) {
            mChildFrom = new int[mOriginalChildFrom.length];
            for (int i = 0; i < mOriginalChildFrom.length; i++) {
                mChildFrom[i] = cursor.getColumnIndexOrThrow(mOriginalChildFrom[i]);
            }
        }
        bindView(view, cursor, mChildFrom, mChildTo);
    }

    private void bindView(View view, Cursor cursor, int[] from, int[] to) {
        if (from == null || to == null) return;
        for (int i = 0; i < to.length; i++) {
            View v = view.findViewById(to[i]);
            if (v != null) {
                String text = cursor.getString(from[i]);
                if (text == null) text = "";
                if (v instanceof TextView) {
                    setViewText((TextView) v, text);
                } else if (v instanceof ImageView) {
                    setViewImage((ImageView) v, text);
                }
            }
        }
    }

    protected void setViewImage(ImageView v, String value) {
        try { v.setImageResource(Integer.parseInt(value)); }
        catch (NumberFormatException nfe) { v.setImageURI(android.net.Uri.parse(value)); }
    }
    protected void setViewText(TextView v, String text) { v.setText(text); }

    // ---- API-36 surface completion ------------------------------------
    // AOSP SimpleCursorTreeAdapter.java :196 / :208 / :310. The ViewBinder
    // interface itself was missing here too, so an app could not even name the
    // type. Mirrors the identical pair on SimpleCursorAdapter, which this tree
    // already implements.
    private ViewBinder mViewBinder;

    public ViewBinder getViewBinder() { return mViewBinder; }
    public void setViewBinder(ViewBinder viewBinder) { mViewBinder = viewBinder; }

    /** AOSP :310 -- lets an app take over binding one column to one view;
     *  returning false hands the binding back to the adapter. */
    public static interface ViewBinder {
        boolean setViewValue(View view, Cursor cursor, int columnIndex);
    }
}
