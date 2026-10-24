// android.database.sqlite.SQLiteTransactionListener -- the callback
// some ORMs / sync layers pass to beginTransactionWithListener so they
// can observe the begin / commit / rollback edges of a SQLite
// transaction (e.g. to flush an in-memory cache only after a commit
// actually lands). Mac.apk invokes these from the matching
// SQLiteDatabase transaction methods.
// Source: frameworks/base/core/java/android/database/sqlite/SQLiteTransactionListener.java
package android.database.sqlite;

public interface SQLiteTransactionListener {
    /** Called immediately after the transaction begins. */
    void onBegin();

    /** Called just before the transaction commits. */
    void onCommit();

    /** Called if the transaction is rolled back. */
    void onRollback();
}
