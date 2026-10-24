// android.content.ContentQueryMap -- caches a Cursor's rows as a
// Map<String, ContentValues> keyed by one column, optionally re-reading it
// whenever the underlying content changes, and notifying java.util.Observers.
//
// AOSP ref: frameworks/base/core/java/android/content/ContentQueryMap.java
// (android16-release). Ported in full, including the DIRTY-when-unobserved
// optimisation (a change with no observers only marks the cache dirty; the
// requery happens on the next read) and the finalize() safety net.
package android.content;

import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Observable;

public class ContentQueryMap extends Observable {
    private volatile Cursor mCursor;
    private String[] mColumnNames;
    private int mKeyColumn;
    private Handler mHandlerForUpdateNotifications = null;
    private boolean mKeepUpdated = false;
    private Map<String, ContentValues> mValues = null;
    private ContentObserver mContentObserver;
    private boolean mDirty = false;

    public ContentQueryMap(Cursor cursor, String columnNameOfKey, boolean keepUpdated,
                           Handler handlerForUpdateNotifications) {
        mCursor = cursor;
        mColumnNames = mCursor.getColumnNames();
        mKeyColumn = mCursor.getColumnIndexOrThrow(columnNameOfKey);
        mHandlerForUpdateNotifications = handlerForUpdateNotifications;
        setKeepUpdated(keepUpdated);
        if (!keepUpdated) {
            readCursorIntoCache(cursor);
        }
    }

    public void setKeepUpdated(boolean keepUpdated) {
        if (keepUpdated == mKeepUpdated) return;
        mKeepUpdated = keepUpdated;
        if (!mKeepUpdated) {
            mCursor.unregisterContentObserver(mContentObserver);
            mContentObserver = null;
        } else {
            if (mHandlerForUpdateNotifications == null) {
                mHandlerForUpdateNotifications = new Handler();
            }
            if (mContentObserver == null) {
                mContentObserver = new ContentObserver(mHandlerForUpdateNotifications) {
                    @Override public void onChange(boolean selfChange) {
                        if (countObservers() != 0) {
                            requery();
                        } else {
                            mDirty = true;
                        }
                    }
                };
            }
            mCursor.registerContentObserver(mContentObserver);
            mDirty = true;
        }
    }

    public synchronized ContentValues getValues(String rowName) {
        if (mDirty) requery();
        return mValues.get(rowName);
    }

    public void requery() {
        final Cursor cursor = mCursor;
        if (cursor == null) {
            return;
        }
        mDirty = false;
        if (!cursor.requery()) {
            return;
        }
        readCursorIntoCache(cursor);
        setChanged();
        notifyObservers();
    }

    private synchronized void readCursorIntoCache(Cursor cursor) {
        int capacity = mValues != null ? mValues.size() : 0;
        mValues = new HashMap<String, ContentValues>(capacity);
        while (cursor.moveToNext()) {
            ContentValues values = new ContentValues();
            for (int i = 0; i < mColumnNames.length; i++) {
                if (i != mKeyColumn) {
                    values.put(mColumnNames[i], cursor.getString(i));
                }
            }
            mValues.put(cursor.getString(mKeyColumn), values);
        }
    }

    public synchronized Map<String, ContentValues> getRows() {
        if (mDirty) requery();
        return mValues;
    }

    public synchronized void close() {
        if (mContentObserver != null) {
            mCursor.unregisterContentObserver(mContentObserver);
            mContentObserver = null;
        }
        mCursor.close();
        mCursor = null;
    }

    @Override
    protected void finalize() throws Throwable {
        if (mCursor != null) close();
        super.finalize();
    }
}
