// android.database.sqlite.SQLiteAbortException -- AOSP API-1+.
package android.database.sqlite;

public class SQLiteAbortException extends SQLiteException {
    public SQLiteAbortException() { super(); }
    public SQLiteAbortException(String error) { super(error); }
}
