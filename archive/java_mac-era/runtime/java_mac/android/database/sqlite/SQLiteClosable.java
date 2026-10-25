// android.database.sqlite.SQLiteClosable -- AOSP refcount-based
// close() base for SQLiteDatabase / SQLiteProgram / SQLiteCursor.
// Mirrors frameworks/base/core/java/android/database/sqlite/
// SQLiteClosable.java. Implements Closeable so try-with-resources
// works against statements + queries.
package android.database.sqlite;

public abstract class SQLiteClosable implements java.io.Closeable {
    private int refCount = 1;

    public void acquireReference() {
        synchronized (this) {
            if (refCount <= 0) {
                throw new IllegalStateException(
                    "attempt to re-open an already-closed object: " + this);
            }
            refCount++;
        }
    }
    public void releaseReference() {
        boolean zero;
        synchronized (this) { zero = (--refCount == 0); }
        if (zero) onAllReferencesReleased();
    }
    public void releaseReferenceFromContainer() { releaseReference(); }

    /** Subclass cleanup hook. SQLiteStatement / SQLiteCursorImpl
     *  finalize their sqlite3_stmt* here. */
    protected void onAllReferencesReleased() {}

    /** AOSP container-release hook (deprecated in AOSP). Default routes
     *  to onAllReferencesReleased so a subclass overriding only the
     *  latter still cleans up. */
    protected void onAllReferencesReleasedFromContainer() { onAllReferencesReleased(); }

    @Override public void close() { releaseReference(); }
}
