// android.database.DatabaseErrorHandler -- callback for SQLite open
// failures. Stub.
package android.database;

import android.database.sqlite.SQLiteDatabase;

public interface DatabaseErrorHandler {
    void onCorruption(SQLiteDatabase dbObj);
}
