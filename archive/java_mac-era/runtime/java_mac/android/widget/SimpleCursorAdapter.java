// android.widget.SimpleCursorAdapter -- binds cursor columns ("from") to
// TextView/ImageView row children ("to"). Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/SimpleCursorAdapter.java
//   public class SimpleCursorAdapter extends ResourceCursorAdapter
package android.widget;

import android.content.Context;
import android.database.Cursor;
import android.view.View;

public class SimpleCursorAdapter extends ResourceCursorAdapter {
    public interface ViewBinder {
        boolean setViewValue(View view, Cursor cursor, int columnIndex);
    }
    public interface CursorToStringConverter {
        CharSequence convertToString(Cursor cursor);
    }

    protected int[] mFrom;
    protected int[] mTo;
    private String[] mOriginalFrom;
    private ViewBinder mViewBinder;
    private CursorToStringConverter mCursorToStringConverter;
    private int mStringConversionColumn = -1;

    @Deprecated
    public SimpleCursorAdapter(Context context, int layout, Cursor c,
            String[] from, int[] to) {
        super(context, layout, c);
        mTo = to;
        mOriginalFrom = from;
        findColumns(c, from);
    }

    public SimpleCursorAdapter(Context context, int layout, Cursor c,
            String[] from, int[] to, int flags) {
        super(context, layout, c, flags);
        mTo = to;
        mOriginalFrom = from;
        findColumns(c, from);
    }

    @Override public void bindView(View view, Context context, Cursor cursor) {
        final ViewBinder binder = mViewBinder;
        final int count = mTo.length;
        final int[] from = mFrom;
        final int[] to = mTo;
        for (int i = 0; i < count; i++) {
            final View v = view.findViewById(to[i]);
            if (v != null) {
                boolean bound = false;
                if (binder != null) bound = binder.setViewValue(v, cursor, from[i]);
                if (!bound) {
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
    }

    public ViewBinder getViewBinder() { return mViewBinder; }
    public void setViewBinder(ViewBinder viewBinder) { mViewBinder = viewBinder; }

    public void setViewImage(ImageView v, String value) {
        try { v.setImageResource(Integer.parseInt(value)); }
        catch (NumberFormatException nfe) { v.setImageURI(android.net.Uri.parse(value)); }
    }
    public void setViewText(TextView v, String text) { v.setText(text); }

    public int getStringConversionColumn() { return mStringConversionColumn; }
    public void setStringConversionColumn(int stringConversionColumn) {
        mStringConversionColumn = stringConversionColumn;
    }
    public CursorToStringConverter getCursorToStringConverter() { return mCursorToStringConverter; }
    public void setCursorToStringConverter(CursorToStringConverter cursorToStringConverter) {
        mCursorToStringConverter = cursorToStringConverter;
    }

    @Override public CharSequence convertToString(Cursor cursor) {
        if (mCursorToStringConverter != null) {
            return mCursorToStringConverter.convertToString(cursor);
        } else if (mStringConversionColumn > -1) {
            return cursor.getString(mStringConversionColumn);
        }
        return super.convertToString(cursor);
    }

    public void changeCursorAndColumns(Cursor c, String[] from, int[] to) {
        mOriginalFrom = from;
        mTo = to;
        findColumns(c, mOriginalFrom);
        changeCursor(c);
    }

    @Override public Cursor swapCursor(Cursor c) {
        Cursor res = super.swapCursor(c);
        findColumns(c, mOriginalFrom);
        return res;
    }

    private void findColumns(Cursor c, String[] from) {
        if (from == null) { mFrom = null; return; }
        if (c != null) {
            int count = from.length;
            if (mFrom == null || mFrom.length != count) mFrom = new int[count];
            for (int i = 0; i < count; i++) {
                mFrom[i] = c.getColumnIndexOrThrow(from[i]);
            }
        } else {
            mFrom = null;
        }
    }
}
