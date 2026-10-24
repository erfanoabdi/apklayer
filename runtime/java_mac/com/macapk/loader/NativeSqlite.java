// NativeSqlite -- thin Java wrapper around macOS's libsqlite3.dylib,
// linked through native_sqlite.cpp + runtime/CMakeLists.txt's
// `sqlite3` target_link_libraries entry. This is the real-SQLite
// engine behind `android.database.sqlite.SQLiteDatabase` so Room,
// WorkManager.WorkDatabase, and every other Room/SQLite-using app
// (F-Droid's RepoDatabase, AppCompat's preferences, every chat app's
// message store) actually persist + query like they do on Android.
//
// Why this exists (load-bearing context for future maintainers):
// pre-v1.0.102, `SQLiteDatabase.rawQuery(...)` returned an empty
// `MatrixCursor` regardless of input, causing Room queries to throw
// `column 'id' does not exist`, which broke the WorkManager init
// chain, which prevented JobScheduler.schedule from ever firing,
// which kept F-Droid's repo refresh from ever running -- the entire
// "F-Droid actually fetches data" story was blocked on this missing
// layer. See CLAUDE.md "The no-shortcuts rule" for the rationale.
//
// AOSP correspondence:
//   The Android-side wrapper that this mirrors is
//   frameworks/base/core/java/android/database/sqlite/SQLiteConnection.java
//   which similarly forwards every primitive to native methods that
//   bridge to libsqlite.so. Their handle types are also `long`.
//
// Threading: each native handle is a sqlite3* / sqlite3_stmt* opened
// with SQLITE_OPEN_FULLMUTEX so SQLite itself serializes internally.
// Java-side SQLiteDatabase still synchronizes for cursor-iteration
// determinism (stepping is non-trivial-state-mutating).
//
// Lifecycle: every handle returned by nativeOpen / nativePrepare MUST
// be released via nativeClose / nativeFinalize. SQLiteDatabase /
// SQLiteCursor / SQLiteStatement own these handles and release in
// close() / finalize() (via cleaner).
package com.macapk.loader;

public final class NativeSqlite {
    // ---- result codes that our Java callers branch on -------------
    public static final int SQLITE_OK   = 0;
    public static final int SQLITE_ROW  = 100;
    public static final int SQLITE_DONE = 101;

    // ---- open flags (sqlite3.h) -----------------------------------
    public static final int SQLITE_OPEN_READONLY  = 0x00000001;
    public static final int SQLITE_OPEN_READWRITE = 0x00000002;
    public static final int SQLITE_OPEN_CREATE    = 0x00000004;
    public static final int SQLITE_OPEN_URI       = 0x00000040;
    // FULLMUTEX is OR'd in unconditionally by the native side.

    // ---- column types ---------------------------------------------
    public static final int SQLITE_INTEGER = 1;
    public static final int SQLITE_FLOAT   = 2;
    public static final int SQLITE_TEXT    = 3;
    public static final int SQLITE_BLOB    = 4;
    public static final int SQLITE_NULL    = 5;

    private NativeSqlite() {}

    // ---- open / close ---------------------------------------------
    /** Returns sqlite3* handle (non-zero) on success, 0 on failure.
     *  Call nativeLastOpenError to get the failure message. */
    public static native long   nativeOpen(String path, int flags);
    public static native int    nativeClose(long dbHandle);
    public static native String nativeLastOpenError();

    // ---- exec (no params) -----------------------------------------
    public static native int    nativeExec(long dbHandle, String sql);
    public static native String nativeLastExecError();

    // ---- prepared statements --------------------------------------
    /** Returns sqlite3_stmt* handle (non-zero) on success, 0 on failure.
     *  On failure, nativeLastExecError reflects the cause. */
    public static native long nativePrepare(long dbHandle, String sql);
    /** Returns SQLITE_ROW (100) when a row is available, SQLITE_DONE
     *  (101) when the statement completed without a row, or an error
     *  code otherwise. */
    public static native int  nativeStep(long stmtHandle);
    public static native int  nativeFinalize(long stmtHandle);
    public static native int  nativeReset(long stmtHandle);
    public static native int  nativeClearBindings(long stmtHandle);

    // ---- bind (1-based index per sqlite3 convention) --------------
    public static native int nativeBindNull(long stmtHandle, int idx);
    public static native int nativeBindLong(long stmtHandle, int idx, long value);
    public static native int nativeBindDouble(long stmtHandle, int idx, double value);
    public static native int nativeBindString(long stmtHandle, int idx, String value);
    public static native int nativeBindBlob(long stmtHandle, int idx, byte[] value);

    // ---- column accessors (0-based index per sqlite3 column API) -
    public static native int    nativeColumnCount(long stmtHandle);
    public static native String nativeColumnName(long stmtHandle, int idx);
    public static native int    nativeColumnType(long stmtHandle, int idx);
    public static native long   nativeColumnLong(long stmtHandle, int idx);
    public static native double nativeColumnDouble(long stmtHandle, int idx);
    public static native String nativeColumnString(long stmtHandle, int idx);
    public static native byte[] nativeColumnBlob(long stmtHandle, int idx);

    // ---- post-DML metadata + error retrieval ----------------------
    public static native int    nativeChanges(long dbHandle);
    public static native long   nativeLastInsertRowid(long dbHandle);
    public static native String nativeErrmsg(long dbHandle);
    public static native String nativeLibversion();
}
