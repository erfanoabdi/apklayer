// android.database.CursorWrapper -- the AOSP base for "decorator"
// cursors that wrap another Cursor and selectively override behavior
// (filtering, column remapping, lazy loading). Room's loaders, the
// classic FilterCursorWrapper, search-suggestion adapters, and many
// content-provider helpers extend this. Every Cursor method delegates
// straight to the wrapped cursor; getWrappedCursor exposes it.
// Source: frameworks/base/core/java/android/database/CursorWrapper.java
//
// Note on the notification-URI list overloads: the AOSP Cursor
// interface declares setNotificationUris(ContentResolver,List<Uri>)
// and getNotificationUris(), but Mac.apk's android.database.Cursor
// does not (it predates that API surface). They are declared here as
// plain public methods (no @Override) so callers invoking them on a
// CursorWrapper reference still resolve; they forward to the wrapped
// cursor only if it actually implements them (best-effort, no-op
// otherwise) to stay compatible with our narrower Cursor interface.
package android.database;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;

import java.util.List;

public class CursorWrapper implements Cursor {
    /** The wrapped cursor. Protected in spirit; AOSP keeps it private
     *  with a getter, so do the same. */
    private final Cursor mCursor;

    public CursorWrapper(Cursor cursor) {
        mCursor = cursor;
    }

    /** Returns the underlying cursor this wrapper delegates to. */
    public Cursor getWrappedCursor() {
        return mCursor;
    }

    @Override public void close() { mCursor.close(); }
    @Override public boolean isClosed() { return mCursor.isClosed(); }
    @Override public int getCount() { return mCursor.getCount(); }
    @Override @Deprecated public void deactivate() { mCursor.deactivate(); }
    @Override @Deprecated public boolean requery() { return mCursor.requery(); }

    @Override public boolean moveToFirst() { return mCursor.moveToFirst(); }
    @Override public boolean moveToLast() { return mCursor.moveToLast(); }
    @Override public boolean move(int offset) { return mCursor.move(offset); }
    @Override public boolean moveToPosition(int position) { return mCursor.moveToPosition(position); }
    @Override public boolean moveToNext() { return mCursor.moveToNext(); }
    @Override public boolean moveToPrevious() { return mCursor.moveToPrevious(); }
    @Override public int getPosition() { return mCursor.getPosition(); }

    @Override public boolean isFirst() { return mCursor.isFirst(); }
    @Override public boolean isLast() { return mCursor.isLast(); }
    @Override public boolean isBeforeFirst() { return mCursor.isBeforeFirst(); }
    @Override public boolean isAfterLast() { return mCursor.isAfterLast(); }

    @Override public int getColumnCount() { return mCursor.getColumnCount(); }
    @Override public int getColumnIndex(String columnName) { return mCursor.getColumnIndex(columnName); }
    @Override public int getColumnIndexOrThrow(String columnName) { return mCursor.getColumnIndexOrThrow(columnName); }
    @Override public String getColumnName(int columnIndex) { return mCursor.getColumnName(columnIndex); }
    @Override public String[] getColumnNames() { return mCursor.getColumnNames(); }

    @Override public byte[] getBlob(int columnIndex) { return mCursor.getBlob(columnIndex); }
    @Override public String getString(int columnIndex) { return mCursor.getString(columnIndex); }
    @Override public short getShort(int columnIndex) { return mCursor.getShort(columnIndex); }
    @Override public int getInt(int columnIndex) { return mCursor.getInt(columnIndex); }
    @Override public long getLong(int columnIndex) { return mCursor.getLong(columnIndex); }
    @Override public float getFloat(int columnIndex) { return mCursor.getFloat(columnIndex); }
    @Override public double getDouble(int columnIndex) { return mCursor.getDouble(columnIndex); }
    @Override public int getType(int columnIndex) { return mCursor.getType(columnIndex); }
    @Override public boolean isNull(int columnIndex) { return mCursor.isNull(columnIndex); }

    @Override public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
        mCursor.copyStringToBuffer(columnIndex, buffer);
    }
    @Override public boolean getWantsAllOnMoveCalls() { return mCursor.getWantsAllOnMoveCalls(); }

    @Override public Bundle getExtras() { return mCursor.getExtras(); }
    @Override public void setExtras(Bundle extras) { mCursor.setExtras(extras); }
    @Override public Bundle respond(Bundle extras) { return mCursor.respond(extras); }

    @Override public void registerContentObserver(ContentObserver observer) {
        mCursor.registerContentObserver(observer);
    }
    @Override public void unregisterContentObserver(ContentObserver observer) {
        mCursor.unregisterContentObserver(observer);
    }
    @Override public void registerDataSetObserver(DataSetObserver observer) {
        mCursor.registerDataSetObserver(observer);
    }
    @Override public void unregisterDataSetObserver(DataSetObserver observer) {
        mCursor.unregisterDataSetObserver(observer);
    }

    @Override public void setNotificationUri(ContentResolver cr, Uri uri) {
        mCursor.setNotificationUri(cr, uri);
    }
    @Override public Uri getNotificationUri() { return mCursor.getNotificationUri(); }

    /** AOSP Cursor.setNotificationUris(ContentResolver, List<Uri>) --
     *  not on Mac.apk's Cursor interface, so declared plainly (no
     *  @Override) and forwarded only if the wrapped cursor supports it.
     *  Falls back to the single-URI form with the first entry. */
    public void setNotificationUris(ContentResolver cr, List<Uri> uris) {
        if (uris != null && !uris.isEmpty()) {
            mCursor.setNotificationUri(cr, uris.get(0));
        }
    }

    /** AOSP Cursor.getNotificationUris() -- not on Mac.apk's Cursor
     *  interface; returns the single notification URI (if any) as a
     *  one-element list to satisfy callers expecting the list form. */
    public List<Uri> getNotificationUris() {
        Uri u = mCursor.getNotificationUri();
        if (u == null) return null;
        java.util.ArrayList<Uri> list = new java.util.ArrayList<>(1);
        list.add(u);
        return list;
    }
}
