// android.database.sqlite.SQLiteDoneException -- thrown when a SQLite
// statement that was expected to return exactly one row returned none
// (sqlite3_step returned SQLITE_DONE on the first step). AOSP's
// SQLiteStatement.simpleQueryForLong / simpleQueryForString throw this
// for an empty result, and DatabaseUtils.longForQuery / stringForQuery
// callers catch it to mean "no rows". Maps from rc=101 (SQLITE_DONE).
// Source: frameworks/base/core/java/android/database/sqlite/SQLiteDoneException.java
package android.database.sqlite;

public class SQLiteDoneException extends SQLiteException {
    public SQLiteDoneException() {
        super("getNumRows returned no rows for the given query");
    }

    public SQLiteDoneException(String error) {
        super(error);
    }
}
