// android.database.sqlite.SQLiteCantOpenDatabaseException -- thrown
// when SQLite can't open a database file. AOSP API-1+.
package android.database.sqlite;

public class SQLiteCantOpenDatabaseException extends SQLiteException {
    public SQLiteCantOpenDatabaseException() { super(); }
    public SQLiteCantOpenDatabaseException(String error) { super(error); }
}
