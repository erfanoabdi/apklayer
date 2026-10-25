// android.database.sqlite.SQLiteDiskIOException -- AOSP API-1+.
package android.database.sqlite;

public class SQLiteDiskIOException extends SQLiteException {
    public SQLiteDiskIOException() { super(); }
    public SQLiteDiskIOException(String error) { super(error); }
}
