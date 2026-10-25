// android.database.sqlite.SQLiteDatabaseCorruptException -- AOSP API-1+.
package android.database.sqlite;

public class SQLiteDatabaseCorruptException extends SQLiteException {
    public SQLiteDatabaseCorruptException() { super(); }
    public SQLiteDatabaseCorruptException(String error) { super(error); }
}
