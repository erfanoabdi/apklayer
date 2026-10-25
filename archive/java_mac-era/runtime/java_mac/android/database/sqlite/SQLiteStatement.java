// android.database.sqlite.SQLiteStatement -- precompiled SQL statement.
// As of v1.0.102, backed by sqlite3_stmt* via NativeSqlite + inherits
// the bind* family from SQLiteProgram (which mirrors AOSP's class
// hierarchy: SQLiteClosable -> SQLiteProgram -> SQLiteStatement).
//
// Returned by SQLiteDatabase.compileStatement. Apps call bindXxx
// (inherited) to set parameters, then execute / executeInsert /
// executeUpdateDelete / simpleQueryForLong / simpleQueryForString
// depending on the expected result shape.
package android.database.sqlite;

import com.macapk.loader.NativeSqlite;

public class SQLiteStatement extends SQLiteProgram {

    /** No-DB constructor: bind/execute become no-ops. Kept so existing
     *  test paths that new'd a raw stmt don't NPE. */
    public SQLiteStatement() { super(null, 0L); }

    /** Real ctor used by SQLiteDatabase.compileStatement. The sql text is
     *  carried so toString() and the bind-index range check can see it. */
    SQLiteStatement(SQLiteDatabase db, long stmt, String sql) { super(db, stmt, sql); }

    /** Step a statement that is NOT supposed to produce rows, with AOSP's
     *  executeNonQuery semantics (core/jni/android_database_SQLiteConnection.cpp):
     *  a SQLITE_ROW from execute / executeInsert / executeUpdateDelete is an
     *  ERROR, not a shrug. That is why db.execSQL("SELECT 1") throws on real
     *  Android -- execSQL routes through executeUpdateDelete -- and it is the
     *  guard rail that stops an app silently running a query for no result and
     *  then wondering where its rows went. Returns sqlite's rc for the caller
     *  to follow up with changes()/last_insert_rowid(). */
    private void stepNonQuery() {
        int rc = NativeSqlite.nativeStep(stmt);
        if (rc == NativeSqlite.SQLITE_DONE) {
            return;
        }
        long handle = (db != null) ? db.macapkDbHandle() : 0L;
        String err = (handle != 0) ? NativeSqlite.nativeErrmsg(handle) : "(no db)";
        NativeSqlite.nativeReset(stmt);
        if (rc == NativeSqlite.SQLITE_ROW) {
            throw new SQLiteException(
                "Queries can be performed using SQLiteDatabase query or rawQuery methods only.");
        }
        throw exceptionForRc(rc, err);
    }

    // The three write-execute paths below run OUTSIDE SQLiteDatabase's
    // synchronized methods (Room's compiled-statement inserts call them
    // directly), so each one takes the database monitor and waits out any
    // OTHER thread's open transaction first (macapkAwaitTxConnection).
    // Without that, a bare write from thread B lands INSIDE thread A's
    // uncommitted transaction on our single connection and silently commits
    // or rolls back with it; on AOSP the write would block acquiring the
    // non-WAL pool's one connection until A finished. Taking the monitor
    // around the step also closes the race where a BEGIN slips in between
    // the ownership check and the step. Reads (simpleQueryFor*) are
    // deliberately not gated; see the transactions comment block in
    // SQLiteDatabase.
    public void execute() {
        acquireReference();
        try {
            if (stmt == 0) return;
            if (db != null) {
                synchronized (db) {
                    db.macapkAwaitTxConnection();
                    stepNonQuery();
                    NativeSqlite.nativeReset(stmt);
                }
                return;
            }
            stepNonQuery();
            NativeSqlite.nativeReset(stmt);
        } finally { releaseReference(); }
    }
    public long executeInsert() {
        acquireReference();
        try {
            if (stmt == 0 || db == null) return -1;
            synchronized (db) {
            db.macapkAwaitTxConnection();
            stepNonQuery();
            long handle = db.macapkDbHandle();
            // AOSP executeForLastInsertedRowId: "err == SQLITE_DONE &&
            // sqlite3_changes(db) > 0 ? sqlite3_last_insert_rowid(db) : -1".
            // The changes() test is what makes INSERT OR IGNORE answer -1 when
            // the conflicting row was left alone, instead of handing back the
            // rowid of whatever unrelated insert happened last. Sync clients
            // branch on exactly that -1.
            int changed = NativeSqlite.nativeChanges(handle);
            long row = (changed > 0) ? NativeSqlite.nativeLastInsertRowid(handle) : -1L;
            NativeSqlite.nativeReset(stmt);
            return row;
            }
        } finally { releaseReference(); }
    }
    public int executeUpdateDelete() {
        acquireReference();
        try {
            if (stmt == 0 || db == null) return 0;
            synchronized (db) {
            db.macapkAwaitTxConnection();
            stepNonQuery();
            int changes = NativeSqlite.nativeChanges(db.macapkDbHandle());
            NativeSqlite.nativeReset(stmt);
            return changes;
            }
        } finally { releaseReference(); }
    }
    public long simpleQueryForLong() {
        acquireReference();
        try {
            if (stmt == 0) return 0;
            int rc = NativeSqlite.nativeStep(stmt);
            if (rc != NativeSqlite.SQLITE_ROW) {
                // AOSP throws SQLiteDoneException when a query that is
                // expected to yield exactly one row returns none (so callers
                // can distinguish "no rows" from a legitimate value of 0).
                // DatabaseUtils.longForQuery propagates this to the app, just
                // like real Android.
                NativeSqlite.nativeReset(stmt);
                throw new SQLiteDoneException();
            }
            long v = NativeSqlite.nativeColumnLong(stmt, 0);
            NativeSqlite.nativeReset(stmt);
            return v;
        } finally { releaseReference(); }
    }
    public String simpleQueryForString() {
        acquireReference();
        try {
            if (stmt == 0) return null;
            int rc = NativeSqlite.nativeStep(stmt);
            if (rc != NativeSqlite.SQLITE_ROW) {
                // AOSP throws SQLiteDoneException for an empty result (see
                // simpleQueryForLong). null is reserved for a row whose first
                // column is genuinely NULL, not for "no row".
                NativeSqlite.nativeReset(stmt);
                throw new SQLiteDoneException();
            }
            String s;
            if (NativeSqlite.nativeColumnType(stmt, 0) == NativeSqlite.SQLITE_BLOB) {
                // AOSP reads this cell with sqlite3_column_text16, which runs
                // the blob's bytes through sqlite's UTF-8 -> UTF-16 decoder and
                // maps every malformed sequence to U+FFFD. Our
                // nativeColumnString does not, so a blob containing a byte that
                // is not valid UTF-8 came back as the Latin-1 character
                // instead of the replacement character. new String(byte[],
                // UTF_8) uses CodingErrorAction.REPLACE, which is the same
                // rule. Note this is deliberately NOT the cursor's answer:
                // Cursor.getString on a BLOB throws (see SQLiteCursorImpl),
                // because that path goes through the CursorWindow instead.
                byte[] b = NativeSqlite.nativeColumnBlob(stmt, 0);
                s = (b == null) ? null : new String(b, java.nio.charset.StandardCharsets.UTF_8);
            } else {
                s = NativeSqlite.nativeColumnString(stmt, 0);
            }
            NativeSqlite.nativeReset(stmt);
            return s;
        } finally { releaseReference(); }
    }
    /** AOSP simpleQueryForBlobFileDescriptor: returns a
     *  ParcelFileDescriptor opened on the first cell's blob (an ashmem
     *  region on real Android). Mac.apk has no ashmem / file-descriptor
     *  blob backing -- blobs are read inline -- so this honestly returns
     *  null (no descriptor available), matching the "no row" return
     *  shape callers handle. */
    public android.os.ParcelFileDescriptor simpleQueryForBlobFileDescriptor() {
        return null;
    }

    /** AOSP routes close() through SQLiteClosable's refcount and finalizes in
     *  onAllReferencesReleased. The distinction is observable: every execute /
     *  query method above opens with acquireReference(), which throws
     *  IllegalStateException("attempt to re-open an already-closed object")
     *  once the count has reached zero. Finalizing directly from close() (as
     *  this did) left a statement whose methods quietly answered 0 / null
     *  after it had been closed, so a use-after-close read as an empty result
     *  instead of as the bug it is. */
    @Override
    protected void onAllReferencesReleased() {
        if (stmt != 0) {
            NativeSqlite.nativeFinalize(stmt);
            stmt = 0;
        }
    }

    /** AOSP SQLiteStatement.toString (SQLiteStatement.java:161-164). Apps and
     *  crash reports print statements; the default Object.toString gave an
     *  identity hash where the sql belongs. */
    @Override public String toString() {
        return "SQLiteProgram: " + getSql();
    }

    /** Map a sqlite3 result code to the closest AOSP exception subclass.
     *  Room and many other Room-using apps catch specific subclasses
     *  (e.g. SQLiteConstraintException for INSERT OR ABORT failure) so
     *  the granularity matters. Codes reference: https://www.sqlite.org/rescode.html
     *  Constants mirrored from AOSP frameworks/base/core/java/android/
     *  database/sqlite/SQLiteException.java + subclasses. */
    static SQLiteException exceptionForRc(int rc, String detail) {
        String msg = "sqlite3 rc=" + rc + ": " + (detail != null ? detail : "(no detail)");
        switch (rc & 0xFF) {  // primary code (low 8 bits per sqlite docs)
            case 19: return new SQLiteConstraintException(msg);
            case 13: return new SQLiteFullException(msg);
            case 7:  return new SQLiteOutOfMemoryException(msg);
            case 6:  return new SQLiteDatabaseLockedException(msg);
            case 11: return new SQLiteDatabaseCorruptException(msg);
            case 10: return new SQLiteDiskIOException(msg);
            case 8:  return new SQLiteReadOnlyDatabaseException(msg);
            case 14: return new SQLiteCantOpenDatabaseException(msg);
            case 18: return new SQLiteBlobTooBigException(msg);
            case 20: return new SQLiteDatatypeMismatchException(msg);
            case 21: return new SQLiteMisuseException(msg);
            case 23: return new SQLiteAccessPermException(msg);
            case 25: return new SQLiteBindOrColumnIndexOutOfRangeException(msg);
            case 4:  return new SQLiteAbortException(msg);
            case 5:  return new SQLiteTableLockedException(msg);
            default: return new SQLiteException(msg);
        }
    }
}
