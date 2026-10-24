// android.database.CursorWindow -- AOSP row buffer for windowed
// cursors. Real Android stores rows in a shared-memory block read by
// AbstractWindowedCursor's getters. Our SQLiteCursorImpl reads values
// directly from the sqlite3_stmt instead, so this window only needs
// to carry the METADATA that app code reads off it -- Google
// Calculator's history layer (minified 'ani') calls
// getWindow().getStartPosition() and getWindow().getNumRows() to size
// its result handling. We back those with real fields and keep a
// minimal in-memory cell store so the AOSP value-accessor surface
// (used by the AbstractWindowedCursor fallback getters) also works.
// AOSP: frameworks/base/core/java/android/database/CursorWindow.java
package android.database;

import android.database.sqlite.SQLiteClosable;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;
import java.util.Map;

public class CursorWindow extends SQLiteClosable implements Parcelable {
    private int startPosition;
    private int numColumns;
    private int numRows;
    // Sparse cell store keyed by (row*numColumns + col). Empty for the
    // SQLite path (values come from the stmt); populated only if a
    // caller actually puts into the window.
    private final Map<Long, Object> cells = new HashMap<>();
    private boolean closed = false;

    public CursorWindow(String name) {}
    public CursorWindow(boolean localWindow) {}
    public CursorWindow(String name, long windowSizeBytes) {}

    public int getStartPosition() { return startPosition; }
    public void setStartPosition(int pos) { startPosition = pos; }
    public int getNumRows() { return numRows; }
    public boolean setNumColumns(int columnNum) { numColumns = columnNum; return true; }
    public boolean allocRow() { numRows++; return true; }
    public void freeLastRow() { if (numRows > 0) numRows--; }
    /** Mac.apk helper: set the row count directly (the SQLite path
     *  knows its count without filling cells). */
    public void macapkSetNumRows(int n) { numRows = n; }

    private long key(int row, int col) {
        return ((long) (row - startPosition) << 20) | (col & 0xFFFFF);
    }
    public boolean putLong(long value, int row, int col)   { cells.put(key(row, col), value); return true; }
    public boolean putDouble(double value, int row, int col){ cells.put(key(row, col), value); return true; }
    public boolean putString(String value, int row, int col){ cells.put(key(row, col), value); return true; }
    public boolean putBlob(byte[] value, int row, int col) { cells.put(key(row, col), value); return true; }
    public boolean putNull(int row, int col)               { cells.put(key(row, col), null); return true; }

    public int getType(int row, int col) {
        Object v = cells.get(key(row, col));
        if (v == null) return Cursor.FIELD_TYPE_NULL;
        if (v instanceof Long) return Cursor.FIELD_TYPE_INTEGER;
        if (v instanceof Double) return Cursor.FIELD_TYPE_FLOAT;
        if (v instanceof byte[]) return Cursor.FIELD_TYPE_BLOB;
        return Cursor.FIELD_TYPE_STRING;
    }
    public byte[] getBlob(int row, int col) {
        Object v = cells.get(key(row, col));
        return (v instanceof byte[]) ? (byte[]) v : null;
    }
    public String getString(int row, int col) {
        Object v = cells.get(key(row, col));
        return v == null ? null : v.toString();
    }
    public long getLong(int row, int col) {
        Object v = cells.get(key(row, col));
        if (v instanceof Long) return (Long) v;
        if (v instanceof Double) return (long) (double) (Double) v;
        try { return v == null ? 0 : Long.parseLong(v.toString()); } catch (Exception e) { return 0; }
    }
    public short getShort(int row, int col) { return (short) getLong(row, col); }
    public int getInt(int row, int col)     { return (int) getLong(row, col); }
    public double getDouble(int row, int col) {
        Object v = cells.get(key(row, col));
        if (v instanceof Double) return (Double) v;
        if (v instanceof Long) return (double) (Long) v;
        try { return v == null ? 0 : Double.parseDouble(v.toString()); } catch (Exception e) { return 0; }
    }
    public float getFloat(int row, int col) { return (float) getDouble(row, col); }

    public void clear() { cells.clear(); numRows = 0; startPosition = 0; }
    public boolean isClosed() { return closed; }

    /** SQLiteClosable terminal hook: free the cell store when the last
     *  reference is released. Direct close() callers (AbstractWindowed
     *  Cursor.setWindow) go through releaseReference() inherited from
     *  SQLiteClosable, which lands here at refcount zero. */
    @Override protected void onAllReferencesReleased() {
        closed = true;
        cells.clear();
    }

    // ---- value type predicates (per (row,col)) -------------------
    // AOSP exposes these so AbstractWindowedCursor can answer
    // is{Null,Blob,Long,Float,String} without materializing the value.

    public boolean isNull(int row, int col)   { return getType(row, col) == Cursor.FIELD_TYPE_NULL; }
    public boolean isBlob(int row, int col) {
        int t = getType(row, col);
        return t == Cursor.FIELD_TYPE_BLOB || t == Cursor.FIELD_TYPE_NULL;
    }
    public boolean isLong(int row, int col)   { return getType(row, col) == Cursor.FIELD_TYPE_INTEGER; }
    public boolean isFloat(int row, int col)  { return getType(row, col) == Cursor.FIELD_TYPE_FLOAT; }
    public boolean isString(int row, int col) {
        int t = getType(row, col);
        return t == Cursor.FIELD_TYPE_STRING || t == Cursor.FIELD_TYPE_NULL;
    }

    /** AOSP copyStringToBuffer(row, col, buffer): copy the cell's
     *  string value into the reusable CharArrayBuffer (the allocation
     *  -avoiding CursorAdapter binding path). */
    public void copyStringToBuffer(int row, int col, CharArrayBuffer buffer) {
        if (buffer == null) return;
        String value = getString(row, col);
        if (value != null) {
            if (buffer.data == null || buffer.data.length < value.length()) {
                buffer.data = value.toCharArray();
            } else {
                value.getChars(0, value.length(), buffer.data, 0);
            }
            buffer.sizeCopied = value.length();
        } else {
            buffer.sizeCopied = 0;
        }
    }

    public int getNumColumns() { return numColumns; }

    // ---- Parcelable ----------------------------------------------
    // AOSP marshals the shared-memory descriptor across Binder. We have
    // no Binder transport (everything is in-process), so the Parcelable
    // surface exists to resolve the type contract; the cell store is
    // not serialized (cross-process windows do not occur in Mac.apk).

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        if (dest == null) return;
        dest.writeInt(startPosition);
        dest.writeInt(numColumns);
        dest.writeInt(numRows);
    }

    public static final Parcelable.Creator<CursorWindow> CREATOR =
        new Parcelable.Creator<CursorWindow>() {
            @Override public CursorWindow createFromParcel(Parcel source) {
                CursorWindow w = new CursorWindow("");
                if (source != null) {
                    w.startPosition = source.readInt();
                    w.numColumns = source.readInt();
                    w.numRows = source.readInt();
                }
                return w;
            }
            @Override public CursorWindow[] newArray(int size) {
                return new CursorWindow[size];
            }
        };

    /** AOSP factory used when reconstructing from a parcel. */
    public static CursorWindow newFromParcel(Parcel p) {
        return CREATOR.createFromParcel(p);
    }
}
