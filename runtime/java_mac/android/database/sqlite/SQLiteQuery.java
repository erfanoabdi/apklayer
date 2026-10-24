// android.database.sqlite.SQLiteQuery -- prepared SELECT statement.
// Used by SQLiteCursor (via SQLiteCursorDriver) + by Room's
// CursorFactory.newCursor callback (which binds args through the
// SQLiteQuery instance before the cursor iterates).
//
// AOSP hierarchy: extends SQLiteProgram (inherits bind*).
//
// Lifecycle: SQLiteDatabase.rawQueryWithFactory creates a SQLiteQuery
// wrapping the prepared stmt, hands it to the factory for arg binds,
// then constructs the cursor. The cursor owns the stmt and finalizes
// it on close; SQLiteQuery is just a binding interface.
package android.database.sqlite;

import android.database.CursorWindow;
import com.macapk.loader.NativeSqlite;

public class SQLiteQuery extends SQLiteProgram {
    public SQLiteQuery() { super(); }
    SQLiteQuery(long stmt) { super(null, stmt); }
    SQLiteQuery(SQLiteDatabase db, long stmt) { super(db, stmt); }

    /** The database this query was prepared on, or null when it was built
     *  from a bare statement handle. AOSP exposes the same value through the
     *  @hide SQLiteProgram.getDatabase(); SQLiteCursor.getDatabase() is the
     *  public path to it. */
    SQLiteDatabase macapkDatabase() { return db; }

    /** AOSP SQLiteQuery.fillWindow: steps the prepared statement and
     *  copies each result row's cells into the window, returning the
     *  number of rows written. AbstractWindowedCursor-derived cursors
     *  drive their value getters off the window this fills.
     *
     *  Note: this consumes the forward-only sqlite3_stmt. Mac.apk's
     *  default SQLiteCursorImpl reads values directly from the stmt and
     *  does NOT call this, so it is only reached by a cursor that
     *  explicitly fills a window (the AOSP windowed path); such a
     *  cursor must not also step the stmt itself.
     *
     *  startPos is the absolute row index of the first row this window
     *  should hold; required is a hint (ignored -- we fill everything
     *  available from startPos). Returns the total number of rows the
     *  statement produced from startPos onward. */
    public int fillWindow(CursorWindow window, int startPos, int requiredPos, boolean countAllRows) {
        if (stmt == 0 || window == null) return 0;
        int colCount = NativeSqlite.nativeColumnCount(stmt);
        window.clear();
        window.setStartPosition(startPos);
        window.setNumColumns(colCount);
        int row = 0;
        int rc;
        while ((rc = NativeSqlite.nativeStep(stmt)) == NativeSqlite.SQLITE_ROW) {
            if (row < startPos) { row++; continue; }
            if (!window.allocRow()) break;
            for (int col = 0; col < colCount; col++) {
                // nativeColumnType returns SQLITE_* codes (not the
                // Cursor.FIELD_TYPE_* codes), so branch on those.
                int type = NativeSqlite.nativeColumnType(stmt, col);
                if (type == NativeSqlite.SQLITE_NULL) {
                    window.putNull(row, col);
                } else if (type == NativeSqlite.SQLITE_INTEGER) {
                    window.putLong(NativeSqlite.nativeColumnLong(stmt, col), row, col);
                } else if (type == NativeSqlite.SQLITE_FLOAT) {
                    window.putDouble(NativeSqlite.nativeColumnDouble(stmt, col), row, col);
                } else if (type == NativeSqlite.SQLITE_BLOB) {
                    window.putBlob(NativeSqlite.nativeColumnBlob(stmt, col), row, col);
                } else {
                    window.putString(NativeSqlite.nativeColumnString(stmt, col), row, col);
                }
            }
            row++;
        }
        return row;
    }
}
