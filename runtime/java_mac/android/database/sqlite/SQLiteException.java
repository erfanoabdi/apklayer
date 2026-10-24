// android.database.sqlite.SQLiteException -- thrown by SQLite ops in
// real Android. Apps catch this in defensive paths around analytics
// and offline-cache layers; we never throw it but it has to exist.
package android.database.sqlite;

import android.database.SQLException;

public class SQLiteException extends SQLException {
    public SQLiteException() {}
    public SQLiteException(String error) { super(error); }
    public SQLiteException(String error, Throwable cause) { super(error, cause); }
}
