// android.database.sqlite.SQLiteReadOnlyDatabaseException -- AOSP API-1+.
package android.database.sqlite;

public class SQLiteReadOnlyDatabaseException extends SQLiteException {
    public SQLiteReadOnlyDatabaseException() { super(); }
    public SQLiteReadOnlyDatabaseException(String error) { super(error); }
}
