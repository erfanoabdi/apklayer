// android.database.sqlite.SQLiteCursor -- the public cursor class
// Room constructs via `new SQLiteCursor(driver, editTable, query)` in
// its CursorFactory lambda. Inherits from SQLiteCursorImpl so all the
// real moveTo/getXxx logic lives in one place; this class is the
// AOSP-shaped public-API constructor.
//
// AOSP signature mirrored:
//   public SQLiteCursor(SQLiteCursorDriver driver, String editTable, SQLiteQuery query)
package android.database.sqlite;

public class SQLiteCursor extends SQLiteCursorImpl {
    /** The driver this cursor calls back on. AOSP keeps it as mDriver and
     *  routes setSelectionArguments() through it. */
    private final SQLiteCursorDriver mDriver;
    /** The database the cursor is associated with. AOSP derives it as
     *  getQuery().getDatabase(); ours comes from SQLiteProgram's own `db`
     *  field, which is the same object by the same route. */
    private final SQLiteDatabase mDatabase;

    /** AOSP API-1+ public ctor used by Room's CursorFactory. */
    public SQLiteCursor(SQLiteCursorDriver driver, String editTable, SQLiteQuery query) {
        // Extract the sqlite3_stmt* from the query; ownership transfers
        // to this cursor (it'll be finalized in close()).
        super(query != null ? query.stmt : 0L);
        mDriver = driver;
        mDatabase = query != null ? query.macapkDatabase() : null;
    }
    /** API-11+ deprecated ctor; ignored database arg + same behavior. */
    public SQLiteCursor(SQLiteDatabase db, SQLiteCursorDriver driver,
                         String editTable, SQLiteQuery query) {
        super(query != null ? query.stmt : 0L);
        mDriver = driver;
        // AOSP's deprecated ctor also ignores `db` and takes the database from
        // the query, but the query is the only place it can come from when the
        // caller passes null for it, so prefer the explicit argument.
        SQLiteDatabase fromQuery = query != null ? query.macapkDatabase() : null;
        mDatabase = fromQuery != null ? fromQuery : db;
    }

    /**
     * AOSP: "Get the database that this cursor is associated with."
     * May be null when the cursor was built from a query that carries no
     * database reference (our SQLiteQuery(long) package ctor does exactly
     * that), which is the same situation AOSP is in when the query has been
     * released -- callers null-check.
     */
    public SQLiteDatabase getDatabase() { return mDatabase; }

    /**
     * AOSP: "Changes the selection arguments. The new values take effect after
     * a call to requery()." Body is `mDriver.setBindArguments(selectionArgs)`
     * -- it does NOT re-bind this cursor's live statement, which is why the
     * documented behaviour is "after requery()". Mirrored exactly; re-binding
     * eagerly here would change results under a caller that is still reading
     * the current rows.
     */
    public void setSelectionArguments(String[] selectionArgs) {
        if (mDriver != null) {
            mDriver.setBindArguments(selectionArgs);
        }
    }

    /** v1.0.502 window-fill direction, from AOSP SQLiteCursor:
     *  "Controls fetching of rows relative to requested position ... if true,
     *  rows will be fetched starting from requested position up to the
     *  window's capacity. Default value is false." androidx.sqlite / Room
     *  helpers downcast a returned Cursor to SQLiteCursor and set this as a
     *  paging hint, so its absence was a hard NoSuchMethodError.
     *
     *  On AOSP the flag picks between DatabaseUtils.cursorPickFillWindowStartPosition()
     *  (bidirectional fill around the requested row) and a strictly forward
     *  fill. SQLiteCursorImpl never fills a CursorWindow at all: it reads each
     *  value straight off the live sqlite3_stmt and can only step forward (see
     *  its header comment), i.e. this cursor is ALREADY permanently
     *  forward-only. Recording the caller's preference is therefore the whole
     *  of the observable contract here; if a future SQLiteCursorImpl ever gains
     *  a real window-backed fill it must consult isFillWindowForwardOnly(). */
    private boolean mFillWindowForwardOnly = false;

    public void setFillWindowForwardOnly(boolean fillWindowForwardOnly) {
        mFillWindowForwardOnly = fillWindowForwardOnly;
    }

    /** Not part of the AOSP public API; the accessor a future window-backed
     *  fill would read. Keeps the stored flag from being dead state. */
    public boolean isFillWindowForwardOnly() { return mFillWindowForwardOnly; }
}
