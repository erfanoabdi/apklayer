// android.database.MergeCursor -- presents an array of cursors as one
// concatenated cursor (rows of cursor[0], then cursor[1], ...). Used
// by ContentProvider result merging, search-suggestion adapters, and
// sync helpers. Navigation maps a global position onto the owning
// sub-cursor; all value getters delegate to whichever sub-cursor is
// currently active. Lifecycle + observer calls fan out to every
// non-null sub-cursor. A DataSetObserver on each sub-cursor resets our
// position when any backing data changes (mirrors AOSP).
// Source: frameworks/base/core/java/android/database/MergeCursor.java
package android.database;

public class MergeCursor extends AbstractCursor {
    private Cursor mCursor; // the currently active sub-cursor
    private final Cursor[] mCursors;

    private final DataSetObserver mObserver = new DataSetObserver() {
        @Override public void onChanged() {
            // Reset our cursor view; the underlying data changed.
            mPos = -1;
        }
        @Override public void onInvalidated() {
            mPos = -1;
        }
    };

    public MergeCursor(Cursor[] cursors) {
        mCursors = cursors;
        mCursor = (cursors != null && cursors.length > 0) ? cursors[0] : null;
        if (cursors != null) {
            for (Cursor c : cursors) {
                if (c != null) c.registerDataSetObserver(mObserver);
            }
        }
    }

    @Override public int getCount() {
        int count = 0;
        if (mCursors != null) {
            for (Cursor c : mCursors) {
                if (c != null) count += c.getCount();
            }
        }
        return count;
    }

    @Override public boolean onMove(int oldPosition, int newPosition) {
        // Find the cursor that contains newPosition by walking the
        // cumulative row counts, then move it to its local position.
        int cursorStart = 0;
        if (mCursors != null) {
            for (Cursor c : mCursors) {
                if (c == null) continue;
                int cnt = c.getCount();
                if (newPosition < cursorStart + cnt) {
                    mCursor = c;
                    return c.moveToPosition(newPosition - cursorStart);
                }
                cursorStart += cnt;
            }
        }
        return false;
    }

    @Override public String getString(int column) {
        return mCursor.getString(column);
    }
    @Override public short getShort(int column) {
        return mCursor.getShort(column);
    }
    @Override public int getInt(int column) {
        return mCursor.getInt(column);
    }
    @Override public long getLong(int column) {
        return mCursor.getLong(column);
    }
    @Override public float getFloat(int column) {
        return mCursor.getFloat(column);
    }
    @Override public double getDouble(int column) {
        return mCursor.getDouble(column);
    }
    @Override public int getType(int column) {
        return mCursor.getType(column);
    }
    @Override public boolean isNull(int column) {
        return mCursor.isNull(column);
    }
    @Override public byte[] getBlob(int column) {
        return mCursor.getBlob(column);
    }

    @Override public String[] getColumnNames() {
        if (mCursor != null) return mCursor.getColumnNames();
        return new String[0];
    }

    @Override public void deactivate() {
        if (mCursors != null) {
            for (Cursor c : mCursors) {
                if (c != null) c.deactivate();
            }
        }
        super.close();
    }

    @Override public void close() {
        if (mCursors != null) {
            for (Cursor c : mCursors) {
                if (c != null) c.close();
            }
        }
        super.close();
    }

    @Override public void registerContentObserver(ContentObserver observer) {
        if (mCursors != null) {
            for (Cursor c : mCursors) {
                if (c != null) c.registerContentObserver(observer);
            }
        }
    }

    @Override public void unregisterContentObserver(ContentObserver observer) {
        if (mCursors != null) {
            for (Cursor c : mCursors) {
                if (c != null) c.unregisterContentObserver(observer);
            }
        }
    }

    @Override public void registerDataSetObserver(DataSetObserver observer) {
        if (mCursors != null) {
            for (Cursor c : mCursors) {
                if (c != null) c.registerDataSetObserver(observer);
            }
        }
    }

    @Override public void unregisterDataSetObserver(DataSetObserver observer) {
        if (mCursors != null) {
            for (Cursor c : mCursors) {
                if (c != null) c.unregisterDataSetObserver(observer);
            }
        }
    }

    @Override public boolean requery() {
        boolean success = true;
        if (mCursors != null) {
            for (Cursor c : mCursors) {
                if (c == null) continue;
                if (!c.requery()) success = false;
            }
        }
        return success;
    }
}
