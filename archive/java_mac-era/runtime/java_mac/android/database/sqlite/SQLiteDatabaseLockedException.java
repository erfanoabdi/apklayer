// android.database.sqlite.SQLiteDatabaseLockedException -- AOSP API-1+.
package android.database.sqlite;

public class SQLiteDatabaseLockedException extends SQLiteException {
    public SQLiteDatabaseLockedException() { super(); }
    public SQLiteDatabaseLockedException(String error) { super(error); }
}
