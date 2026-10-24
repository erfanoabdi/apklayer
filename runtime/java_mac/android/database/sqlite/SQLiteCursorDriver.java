// android.database.sqlite.SQLiteCursorDriver -- AOSP's callback channel between
// a SQLiteCursor and the database (the cursor calls back on close / deactivate /
// requery and to (re)bind args).
//
// KIND: an INTERFACE. AOSP:
//     public interface SQLiteCursorDriver {
//         Cursor query(CursorFactory factory, String[] bindArgs);
//         void cursorDeactivated();
//         void cursorRequeried(Cursor cursor);
//         void cursorClosed();
//         public void setBindArguments(String[] bindArgs);
//     }
// (frameworks/base/core/java/android/database/sqlite/SQLiteCursorDriver.java:26,
// android14-release), confirmed against the platform jar:
//     javap -p 'android.database.sqlite.SQLiteCursorDriver'
//     -> public interface ... { public abstract android.database.Cursor query(...); ... }
//
// It used to be a CLASS here, with the comment "Kept as a CLASS (AOSP declares an
// interface, but our SQLiteDatabase constructs one with `new SQLiteCursorDriver()`)".
// That reasoning was backwards: the whole point of the type is that an app or
// library supplies its OWN driver, so a guest class saying
// `implements SQLiteCursorDriver` failed to LINK with IncompatibleClassChangeError
// before any of its code ran -- exactly the failure this type exists to enable.
// The single in-tree construction moved to SQLiteDirectCursorDriver, which is what
// AOSP itself instantiates (SQLiteDirectCursorDriver.java:30,
// `public final class SQLiteDirectCursorDriver implements SQLiteCursorDriver`).
package android.database.sqlite;

import android.database.Cursor;

public interface SQLiteCursorDriver {
    Cursor query(SQLiteDatabase.CursorFactory factory, String[] bindArgs);
    void cursorDeactivated();
    void cursorRequeried(Cursor cursor);
    void cursorClosed();
    void setBindArguments(String[] bindArgs);
}
