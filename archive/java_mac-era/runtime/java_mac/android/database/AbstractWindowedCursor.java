// android.database.AbstractWindowedCursor -- AOSP cursor whose row
// data lives in a CursorWindow. SQLiteCursor extends this; app code
// (Google Calculator's history DB layer, minified 'amm'/'ani') casts
// the query result to AbstractWindowedCursor and reads
// getWindow().getStartPosition()/getNumRows() for sizing, then reads
// row values through the Cursor interface. So the cast must succeed
// AND getWindow() must return a window with correct row metadata.
// AOSP: frameworks/base/core/java/android/database/AbstractWindowedCursor.java
//   (extends AbstractCursor; field protected CursorWindow mWindow;
//    getWindow/setWindow/hasWindow; value getters delegate to mWindow)
//
// Our concrete SQLite cursor (SQLiteCursorImpl) overrides the value
// getters to read directly from the sqlite3_stmt, so the window here
// only needs to carry metadata (numRows/startPosition). The
// window-backed getters below are kept for AOSP fidelity and for any
// other windowed cursor that doesn't override them.
package android.database;

public abstract class AbstractWindowedCursor extends AbstractCursor {
    protected CursorWindow mWindow;

    @Override public byte[] getBlob(int columnIndex) {
        checkPosition();
        return mWindow.getBlob(mPos, columnIndex);
    }
    @Override public String getString(int columnIndex) {
        checkPosition();
        return mWindow.getString(mPos, columnIndex);
    }
    @Override public short getShort(int columnIndex) {
        checkPosition();
        return mWindow.getShort(mPos, columnIndex);
    }
    @Override public int getInt(int columnIndex) {
        checkPosition();
        return mWindow.getInt(mPos, columnIndex);
    }
    @Override public long getLong(int columnIndex) {
        checkPosition();
        return mWindow.getLong(mPos, columnIndex);
    }
    @Override public float getFloat(int columnIndex) {
        checkPosition();
        return mWindow.getFloat(mPos, columnIndex);
    }
    @Override public double getDouble(int columnIndex) {
        checkPosition();
        return mWindow.getDouble(mPos, columnIndex);
    }
    @Override public boolean isNull(int columnIndex) {
        checkPosition();
        return mWindow.getType(mPos, columnIndex) == Cursor.FIELD_TYPE_NULL;
    }
    // AOSP defines all four in terms of the VIRTUAL getType (AbstractWindowed
    // Cursor.java:78-110), not in terms of mWindow directly. That indirection
    // is load-bearing here: SQLiteCursorImpl reads its values off the live
    // statement and keeps only a metadata window, so asking mWindow for a cell
    // type answered FIELD_TYPE_NULL for every column. Going through getType
    // picks up the subclass's real answer.
    public boolean isBlob(int columnIndex) {
        return getType(columnIndex) == Cursor.FIELD_TYPE_BLOB;
    }
    public boolean isString(int columnIndex) {
        return getType(columnIndex) == Cursor.FIELD_TYPE_STRING;
    }
    public boolean isLong(int columnIndex) {
        return getType(columnIndex) == Cursor.FIELD_TYPE_INTEGER;
    }
    public boolean isFloat(int columnIndex) {
        return getType(columnIndex) == Cursor.FIELD_TYPE_FLOAT;
    }
    public int getType(int columnIndex) {
        checkPosition();
        return mWindow.getType(mPos, columnIndex);
    }

    @Override protected void checkPosition() {
        // AOSP AbstractWindowedCursor.checkPosition: first the base
        // position bounds check (throws CursorIndexOutOfBoundsException),
        // then assert a window is present.
        super.checkPosition();
        if (mWindow == null) {
            throw new android.database.StaleDataException(
                "Access to this cursor has expired (no window)");
        }
    }

    @Override public CursorWindow getWindow() { return mWindow; }
    public void setWindow(CursorWindow window) {
        if (mWindow != window) {
            if (mWindow != null) mWindow.close();
            mWindow = window;
        }
    }
    public boolean hasWindow() { return mWindow != null; }
}
