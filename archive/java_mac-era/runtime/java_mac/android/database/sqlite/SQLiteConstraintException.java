// android.database.sqlite.SQLiteConstraintException -- thrown on
// constraint violations (UNIQUE / FOREIGN KEY / etc.). AOSP API-1+.
package android.database.sqlite;

public class SQLiteConstraintException extends SQLiteException {
    public SQLiteConstraintException() { super(); }
    public SQLiteConstraintException(String error) { super(error); }
}
