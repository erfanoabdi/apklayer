// android.database.sqlite.SQLiteAccessPermException -- thrown when
// the app lacks permission to access a DB file. AOSP API-1+. F-Droid's
// Room/SQLite layer catches this in its error-recovery branches; the
// class just needs to exist for catch blocks to link.
package android.database.sqlite;

// AOSP hierarchy: SQLiteAccessPermException extends
// SQLiteDiskIOException (which extends SQLiteException). The previous
// impl extended SQLiteException directly, so a catch (SQLiteDiskIO
// Exception) block in app code did not catch a permission error. Fixed
// to the AOSP chain.
// AOSP: `extends SQLiteException` directly, NOT SQLiteDiskIOException. Hanging it
// off DiskIO makes `catch (SQLiteDiskIOException)` swallow a permission failure
// that on Android would propagate.
// frameworks/base/core/java/android/database/sqlite/SQLiteAccessPermException.java
public class SQLiteAccessPermException extends SQLiteException {
    public SQLiteAccessPermException() { super(); }
    public SQLiteAccessPermException(String error) { super(error); }
}
