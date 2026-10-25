// android.widget.ResourceCursorAdapter -- CursorAdapter that inflates a fixed
// layout for each row. Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/ResourceCursorAdapter.java
//   public abstract class ResourceCursorAdapter extends CursorAdapter
package android.widget;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public abstract class ResourceCursorAdapter extends CursorAdapter {
    private int mLayout;
    private int mDropDownLayout;
    private LayoutInflater mInflater;
    private LayoutInflater mDropDownInflater;

    @Deprecated
    public ResourceCursorAdapter(Context context, int layout, Cursor c) {
        super(context, c);
        mLayout = mDropDownLayout = layout;
        mInflater = LayoutInflater.from(context);
    }

    public ResourceCursorAdapter(Context context, int layout, Cursor c, boolean autoRequery) {
        super(context, c, autoRequery);
        mLayout = mDropDownLayout = layout;
        mInflater = LayoutInflater.from(context);
    }

    public ResourceCursorAdapter(Context context, int layout, Cursor c, int flags) {
        super(context, c, flags);
        mLayout = mDropDownLayout = layout;
        mInflater = LayoutInflater.from(context);
    }

    @Override public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return mInflater.inflate(mLayout, parent, false);
    }

    @Override public View newDropDownView(Context context, Cursor cursor, ViewGroup parent) {
        return (mDropDownInflater == null ? mInflater : mDropDownInflater)
                .inflate(mDropDownLayout, parent, false);
    }

    public void setViewResource(int layout) { mLayout = layout; }
    public void setDropDownViewResource(int dropDownLayout) { mDropDownLayout = dropDownLayout; }
}
