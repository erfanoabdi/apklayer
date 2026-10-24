// android.database.sqlite.SQLiteOpenHelper -- subclassed by apps to
// manage a versioned SQLite schema. As of v1.0.102, opens a real
// file under Context.getDatabasePath(name) and runs the version-
// migration callbacks (onCreate / onUpgrade / onDowngrade /
// onConfigure / onOpen) just like AOSP does.
//
// AOSP mirror: frameworks/base/core/java/android/database/sqlite/
//   SQLiteOpenHelper.java. Their version dance is:
//     1. onConfigure(db)
//     2. if version == 0: onCreate(db); setVersion(targetVersion)
//        else if version < target: onUpgrade(db, old, new); setVersion
//        else if version > target: onDowngrade(db, old, new); setVersion
//     3. onOpen(db)
//   We mirror exactly, with the version stored in PRAGMA user_version.
//
// The whole point of this class for our scenario: WorkManager's
// WorkDatabase extends RoomDatabase -> SupportSQLiteOpenHelper which
// extends our SQLiteOpenHelper. Its onCreate creates the WorkSpec,
// Configuration, Preference, Dependency, etc. tables. Without those,
// every Room query against "WorkSpec" fails with "no such table"
// (or worse, "column 'id' does not exist" because the query joins
// nothing). Real onCreate behavior unblocks Room end-to-end.
package android.database.sqlite;

import android.content.Context;
import java.io.File;

// Implements AutoCloseable so try-with-resources (and AOSP's
// API 16+ contract) resolves -- some androidx helpers close the open
// helper via the AutoCloseable interface.
public abstract class SQLiteOpenHelper implements AutoCloseable {
    private final Context context;
    private final String  name;
    // NOT final since API 28: setOpenParams() may replace the cursor factory
    // and error handler before the database is first opened.
    private SQLiteDatabase.CursorFactory factory;
    private final int     newVersion;
    private android.database.DatabaseErrorHandler errorHandler;
    /** The OpenParams last supplied, applied when the database is opened. */
    private SQLiteDatabase.OpenParams openParams;

    /** Open db, lazily, the first time getWritableDatabase or
     *  getReadableDatabase is called. */
    private SQLiteDatabase database;
    private boolean writeAheadLoggingEnabled;
    private boolean initializing;

    public SQLiteOpenHelper(Context context, String name,
                             SQLiteDatabase.CursorFactory factory, int version) {
        this(context, name, factory, version, null);
    }
    public SQLiteOpenHelper(Context context, String name,
                             SQLiteDatabase.CursorFactory factory, int version,
                             android.database.DatabaseErrorHandler errorHandler) {
        if (version < 1) throw new IllegalArgumentException("version must be >= 1, was " + version);
        this.context      = context;
        this.name         = name;
        this.factory      = factory;
        this.newVersion   = version;
        this.errorHandler = errorHandler;
    }

    /** AOSP API 28 ctor taking an OpenParams bag. We pull the cursor
     *  factory, error handler, and WAL preference out of it; the other
     *  open options (lookaside, idle timeout) are no-ops on our backing
     *  but are carried so the surface resolves. */
    public SQLiteOpenHelper(Context context, String name, int version,
                             SQLiteDatabase.OpenParams openParams) {
        if (version < 1) throw new IllegalArgumentException("version must be >= 1, was " + version);
        this.context      = context;
        this.name         = name;
        this.factory      = (openParams != null) ? openParams.getCursorFactory() : null;
        this.newVersion   = version;
        this.errorHandler = (openParams != null) ? openParams.getErrorHandler() : null;
        this.openParams   = openParams;
    }

    /**
     * AOSP (API 28): "Sets configuration parameters that are used for opening
     * SQLiteDatabase. Please note that SQLiteDatabase.OpenParams.Builder#setOpenFlags(int)
     * is not supported by SQLiteOpenHelper."
     * @throws IllegalStateException if the database is already open.
     *
     * The throw is load-bearing rather than defensive: silently accepting
     * params after the open would leave the caller believing a journal mode or
     * cursor factory took effect when the live connection is still using the
     * old one.
     */
    public void setOpenParams(SQLiteDatabase.OpenParams openParams) {
        java.util.Objects.requireNonNull(openParams);
        synchronized (this) {
            if (database != null && database.isOpen()) {
                throw new IllegalStateException(
                        "OpenParams cannot be set after opening the database");
            }
            this.openParams = openParams;
            // AOSP's SQLiteOpenHelper reads the factory + error handler out of
            // the params at open time; carrying them into the fields now is the
            // same thing one step earlier, and keeps the two constructors and
            // this setter converging on one code path.
            if (openParams.getCursorFactory() != null) {
                this.factory = openParams.getCursorFactory();
            }
            if (openParams.getErrorHandler() != null) {
                this.errorHandler = openParams.getErrorHandler();
            }
        }
    }

    // AOSP routes BOTH accessors into one private getDatabaseLocked(writable)
    // (frameworks/base core/java/android/database/sqlite/SQLiteOpenHelper.java,
    // getReadableDatabase -> getDatabaseLocked(false)). getReadableDatabase must
    // NOT call the public getWritableDatabase: a subclass may override that one
    // to refuse writes and still read through super.getReadableDatabase(), and a
    // virtual call would land in the override. Rangskat's SearchDBHelper does
    // exactly that (getWritableDatabase throws "database is not writable"), so
    // the old delegation threw on every read and the dictionary never loaded.
    public SQLiteDatabase getWritableDatabase() {
        synchronized (this) {
            return getDatabaseLocked(true);
        }
    }

    public SQLiteDatabase getReadableDatabase() {
        synchronized (this) {
            return getDatabaseLocked(false);
        }
    }

    /** Bisect gate for the dispatch above: MACAPK_SQLITE_READABLE_PRIVATE=0
     *  restores the pre-fix virtual call into getWritableDatabase(). */
    private static final boolean MACAPK_READABLE_PRIVATE =
            !"0".equals(System.getenv("MACAPK_SQLITE_READABLE_PRIVATE"));

    private SQLiteDatabase getDatabaseLocked(boolean writable) {
        if (!MACAPK_READABLE_PRIVATE && !writable) {
            return getWritableDatabase();
        }
        if (database != null) {
            if (!database.isOpen()) {
                // The app closed the database itself.
                database = null;
            } else if (!writable || !database.isReadOnly()) {
                return database;
            }
        }
        if (initializing) {
            throw new IllegalStateException("getDatabase called recursively");
        }

        SQLiteDatabase db = database;
        try {
            initializing = true;
            if (db != null) {
                if (writable && db.isReadOnly()) {
                    db.reopenReadWrite();
                }
            } else {
                File path = (name == null || ":memory:".equals(name))
                    ? null
                    : (context != null ? context.getDatabasePath(name) : new File(name));
                String dbPath = path != null ? path.getAbsolutePath() : ":memory:";
                if (path != null && path.getParentFile() != null) {
                    path.getParentFile().mkdirs();
                }
                try {
                    db = SQLiteDatabase.openDatabase(dbPath, factory,
                        SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY);
                } catch (SQLiteException ex) {
                    // AOSP: only a READABLE request falls back to read-only.
                    if (writable || path == null) throw ex;
                    android.util.Log.e("SQLiteOpenHelper",
                        "Couldn't open " + name + " for writing (will try read-only):", ex);
                    db = SQLiteDatabase.openDatabase(dbPath, factory,
                        SQLiteDatabase.OPEN_READONLY);
                }
                if (openParams != null) {
                    // Journal / synchronous mode from the params bag. AOSP applies
                    // these through SQLiteDatabase's own open path; ours are
                    // PRAGMAs on the freshly-opened connection, which is where
                    // SQLite itself applies them either way.
                    db.macapkApplyOpenParams(openParams);
                }
                if (!db.isReadOnly()) {
                    if (writeAheadLoggingEnabled) {
                        db.enableWriteAheadLogging();
                    } else {
                        db.disableWriteAheadLogging();
                    }
                }
            }

            // ----- migration dance (AOSP order) -----------------------
            onConfigure(db);
            int currentVersion = db.getVersion();
            if (currentVersion != newVersion) {
                if (db.isReadOnly()) {
                    throw new SQLiteException("Can't upgrade read-only database from version "
                        + currentVersion + " to " + newVersion + ": " + name);
                }
                db.beginTransaction();
                try {
                    if (currentVersion == 0) {
                        onCreate(db);
                    } else if (currentVersion > newVersion) {
                        onDowngrade(db, currentVersion, newVersion);
                    } else {
                        onUpgrade(db, currentVersion, newVersion);
                    }
                    db.setVersion(newVersion);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            onOpen(db);
            database = db;
            return db;
        } finally {
            initializing = false;
            // AOSP: a database opened by this call that did not become the
            // helper's database (a callback threw) is closed, so the next call
            // re-runs the version dance instead of handing out a half-set-up db.
            if (db != null && db != database) {
                db.close();
            }
        }
    }

    public synchronized void close() {
        if (database != null) {
            database.close();
            database = null;
        }
    }
    public String getDatabaseName() { return name; }

    public synchronized void setWriteAheadLoggingEnabled(boolean enabled) {
        this.writeAheadLoggingEnabled = enabled;
        if (database != null && database.isOpen()) {
            if (enabled) database.enableWriteAheadLogging();
            else         database.disableWriteAheadLogging();
        }
    }
    public synchronized boolean isWriteAheadLoggingEnabled() {
        return writeAheadLoggingEnabled;
    }
    public synchronized void setIdleConnectionTimeout(long ms) {}
    public synchronized void setLookasideConfig(int slotSize, int slotCount) {}

    // ---- callbacks the subclass implements ------------------------
    public abstract void onCreate(SQLiteDatabase db);
    public abstract void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new SQLiteException("Can't downgrade database from v"
            + oldVersion + " to v" + newVersion);
    }
    public void onOpen(SQLiteDatabase db) {}
    public void onConfigure(SQLiteDatabase db) {}
}
