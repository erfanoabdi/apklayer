// android.database.sqlite.SQLiteOutOfMemoryException -- AOSP API-1+.
package android.database.sqlite;

public class SQLiteOutOfMemoryException extends SQLiteException {
    public SQLiteOutOfMemoryException() { super(); }
    public SQLiteOutOfMemoryException(String error) { super(error); }
}
