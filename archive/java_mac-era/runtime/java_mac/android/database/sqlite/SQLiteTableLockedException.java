// android.database.sqlite.SQLiteTableLockedException -- AOSP API-1+.
package android.database.sqlite;

public class SQLiteTableLockedException extends SQLiteException {
    public SQLiteTableLockedException() { super(); }
    public SQLiteTableLockedException(String error) { super(error); }
}
