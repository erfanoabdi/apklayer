// android.database.sqlite.SQLiteDirectCursorDriver -- AOSP's own implementation
// of SQLiteCursorDriver, and the one the framework instantiates.
//
// Source shape: frameworks/base/core/java/android/database/sqlite/
// SQLiteDirectCursorDriver.java:30 (android14-release):
//     public final class SQLiteDirectCursorDriver implements SQLiteCursorDriver {
//         public SQLiteDirectCursorDriver(SQLiteDatabase db, String sql,
//                                         String editTable, CancellationSignal cs)
//         public Cursor query(CursorFactory factory, String[] selectionArgs)
//         public void cursorClosed() / setBindArguments(String[]) /
//                     cursorDeactivated() / cursorRequeried(Cursor)
//     }
// We drop the trailing CancellationSignal because Mac.apk does not model query
// cancellation; the argument ORDER of the rest matches AOSP so a reader of that
// file finds what they expect. This is package-visible framework plumbing, not
// SDK surface, so no guest compiles against the constructor.
//
// This class carries the behaviour that used to live in SQLiteCursorDriver when
// that type was (wrongly) a class: Mac.apk's SQLiteCursorImpl drives the
// rawQueryWithFactory path directly and does not call back, so the notification
// methods are benign no-ops -- but any AndroidX/app cursor subclass that DOES
// invoke the driver (Room's older FrameworkSQLiteDatabase path, a custom
// CursorFactory) needs the method surface to resolve instead of
// NoSuchMethodError'ing. v1.0.426 Tier-2.
package android.database.sqlite;

import android.database.Cursor;

public final class SQLiteDirectCursorDriver implements SQLiteCursorDriver {
    // When constructed with a db + sql (the AOSP shape), query() can actually run
    // the SELECT through the factory. SQLiteDatabase.rawQueryWithFactory drives
    // the cursor itself, so it passes nulls and query() is a benign no-op.
    private final SQLiteDatabase mDatabase;
    private final String mSql;
    private final String mEditTable;
    private String[] mBindArgs;

    public SQLiteDirectCursorDriver(SQLiteDatabase db, String sql, String editTable) {
        mDatabase = db;
        mSql = sql;
        mEditTable = editTable;
    }

    /** Compile + run the SQL, invoking the factory to build the cursor.
     *  Returns null only when this driver has no db/sql (the internal
     *  path, where the database drives the cursor directly). */
    @Override
    public Cursor query(SQLiteDatabase.CursorFactory factory, String[] bindArgs) {
        if (mDatabase == null || mSql == null) return null;
        String[] args = (bindArgs != null) ? bindArgs : mBindArgs;
        return mDatabase.rawQueryWithFactory(factory, mSql, args, mEditTable);
    }

    @Override public void cursorClosed() {}
    @Override public void setBindArguments(String[] bindArgs) { mBindArgs = bindArgs; }
    @Override public void cursorDeactivated() {}
    @Override public void cursorRequeried(Cursor cursor) {}
}
