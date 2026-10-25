// android.database.sqlite.SQLiteMisuseException -- AOSP API-1+.
package android.database.sqlite;

public class SQLiteMisuseException extends SQLiteException {
    public SQLiteMisuseException() { super(); }
    public SQLiteMisuseException(String error) { super(error); }
}
