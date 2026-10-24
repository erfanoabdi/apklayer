// android.database.AbstractCursor -- AOSP base class for Cursor
// implementations. Real AOSP leaves the value getters + getCount/
// getColumnNames abstract and provides concrete navigation; we give
// every method a concrete default so the class compiles standalone
// and any subclass that doesn't override a method still resolves
// (observably identical -- every real subclass overrides what it
// needs). The load-bearing reason this class exists with real
// content: app code (Google Calculator's history DB layer) casts the
// SQLite query result to AbstractWindowedCursor; that requires
// SQLiteCursorImpl -> AbstractWindowedCursor -> AbstractCursor ->
// Cursor to be a real chain. AOSP:
//   frameworks/base/core/java/android/database/AbstractCursor.java
//   (AbstractCursor implements CrossProcessCursor, Cursor)
package android.database;

import android.content.ContentResolver;
import android.net.Uri;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

// AOSP declares "implements CrossProcessCursor" (which itself extends
// Cursor); mirror it so app/loader code that branches on
// `instanceof CrossProcessCursor` and calls fillWindow/getWindow on
// the result resolves. Every AbstractCursor subclass (MatrixCursor,
// MergeCursor, AbstractWindowedCursor/SQLiteCursor) thus also IS a
// CrossProcessCursor, matching real Android.
// AOSP fidelity: the deprecation usage below MIRRORS AOSP, which deprecates its
// own APIs while continuing to implement them. Removing it would diverge
// from the surface we exist to reproduce, so it is suppressed here rather
// than globally -- every other lint category stays live for this file.
@SuppressWarnings("deprecation")
public abstract class AbstractCursor implements CrossProcessCursor {
    protected int mPos = -1;
    /**
     * AOSP declares this PROTECTED and @Deprecated
     * (core/api/current.txt: "field @Deprecated protected boolean mClosed;"),
     * not private. Subclasses in real apps read it directly -- that is the
     * whole reason it is in the public API -- so a private field here means a
     * guest AbstractCursor subclass fails to resolve it.
     */
    @Deprecated
    protected boolean mClosed = false;

    // Subclasses MUST provide these (AOSP marks them abstract).
    @Override public abstract int getCount();
    @Override public abstract String[] getColumnNames();
    @Override public abstract String getString(int column);
    @Override public abstract short getShort(int column);
    @Override public abstract int getInt(int column);
    @Override public abstract long getLong(int column);
    @Override public abstract float getFloat(int column);
    @Override public abstract double getDouble(int column);
    @Override public abstract boolean isNull(int column);

    @Override public byte[] getBlob(int column) {
        throw new UnsupportedOperationException("getBlob is not supported");
    }
    @Override public int getColumnCount() { return getColumnNames().length; }

    // ---- navigation (concrete defaults; SQLiteCursorImpl overrides
    // with a forward-only stmt-stepping variant, so these stay
    // overridable -- NOT final) ----
    @Override public int getPosition() { return mPos; }
    @Override public boolean moveToPosition(int position) {
        final int count = getCount();
        if (position >= count) { mPos = count; return false; }
        if (position < 0) { mPos = -1; return false; }
        final int oldPos = mPos;
        boolean result = onMove(oldPos, position);
        if (!result) {
            mPos = -1;
        } else {
            mPos = position;
        }
        return result;
    }
    /** AOSP onMove hook -- window cursors override to fill the window;
     *  default just accepts the move. Public to satisfy the
     *  CrossProcessCursor interface (AOSP declares it @Override public). */
    @Override public boolean onMove(int oldPosition, int newPosition) { return true; }

    // ---- CrossProcessCursor surface (AOSP AbstractCursor implements it) ----
    // Non-windowed cursors have no window; getWindow returns null and
    // fillWindow synthesizes one from the row values via the generic
    // DatabaseUtils helper (exactly AOSP's bodies). AbstractWindowedCursor
    // overrides getWindow to return its real mWindow.
    @Override public CursorWindow getWindow() { return null; }
    @Override public void fillWindow(int position, CursorWindow window) {
        DatabaseUtils.cursorFillWindow(this, position, window);
    }

    /** AOSP AbstractCursor.checkPosition: throws
     *  CursorIndexOutOfBoundsException when the cursor sits before the
     *  first row (-1) or at/after the last (== count). Value getters in
     *  AOSP subclasses call this before reading. */
    protected void checkPosition() {
        if (-1 == mPos || getCount() == mPos) {
            throw new CursorIndexOutOfBoundsException(mPos, getCount());
        }
    }
    @Override public boolean move(int offset) { return moveToPosition(mPos + offset); }
    @Override public boolean moveToFirst() { return moveToPosition(0); }
    @Override public boolean moveToLast() { return moveToPosition(getCount() - 1); }
    @Override public boolean moveToNext() { return moveToPosition(mPos + 1); }
    @Override public boolean moveToPrevious() { return moveToPosition(mPos - 1); }
    @Override public boolean isFirst() { return mPos == 0 && getCount() != 0; }
    @Override public boolean isLast() { int c = getCount(); return mPos == (c - 1) && c != 0; }
    @Override public boolean isBeforeFirst() { return getCount() == 0 || mPos == -1; }
    @Override public boolean isAfterLast() { return getCount() == 0 || mPos == getCount(); }
    @Override public boolean isClosed() { return mClosed; }

    @Override public int getColumnIndex(String columnName) {
        final String[] cols = getColumnNames();
        // AOSP strips a trailing ".<col>" qualifier; mirror it.
        final int periodIndex = columnName == null ? -1 : columnName.lastIndexOf('.');
        if (periodIndex != -1) columnName = columnName.substring(periodIndex + 1);
        if (cols != null) {
            for (int i = 0; i < cols.length; i++) {
                if (cols[i].equalsIgnoreCase(columnName)) return i;
            }
        }
        return -1;
    }
    @Override public int getColumnIndexOrThrow(String columnName) {
        final int index = getColumnIndex(columnName);
        if (index < 0) {
            throw new IllegalArgumentException("column '" + columnName + "' does not exist");
        }
        return index;
    }
    /** AOSP is a bare array index: `return getColumnNames()[columnIndex]`
     *  (AbstractCursor.java:344-347). An out-of-range index is therefore an
     *  ArrayIndexOutOfBoundsException, NOT null. Returning null here looked
     *  defensive but moved the failure: the caller NPEs somewhere else, with
     *  no indication that its column index was wrong. */
    @Override public String getColumnName(int columnIndex) {
        return getColumnNames()[columnIndex];
    }

    // ---- observer plumbing (AOSP AbstractCursor) ------------------------
    // These were empty bodies, which is the worst failure shape: every
    // registerContentObserver / setNotificationUri call resolved cleanly and
    // did nothing. AOSP's contract is a two-hop chain -- the cursor registers
    // ONE "self observer" with the ContentResolver at the URI the query came
    // from, and when a ContentResolver.notifyChange(uri) fires anywhere in the
    // process that self observer calls AbstractCursor.onChange, which fans the
    // notification out to every app-registered ContentObserver via
    // mContentObservable. That fan-out is the mechanism CursorAdapter /
    // CursorLoader / SearchView suggestion adapters rely on to auto-refresh;
    // without it they show stale rows until the app is restarted.
    // AOSP: frameworks/base/core/java/android/database/AbstractCursor.java
    private final DataSetObservable mDataSetObservable = new DataSetObservable();
    private final ContentObservable mContentObservable = new ContentObservable();

    private final Object mSelfObserverLock = new Object();
    private ContentObserver mSelfObserver;
    private boolean mSelfObserverRegistered;
    private Uri mNotifyUri;
    private List<Uri> mNotifyUris;

    /** @deprecated AOSP exposes the resolver the cursor was told to notify
     *  through as a protected field; subclasses read it. */
    @Deprecated
    protected ContentResolver mContentResolver;

    /**
     * AOSP's finalize: unregister the self observer, then close if the cursor
     * was leaked without a close(). It swallows every exception on purpose --
     * a throw out of finalize() on the finalizer thread would take down an
     * unrelated part of the process, and there is nobody left to report to.
     *
     * This is a REAL safety net, not decoration: an AbstractCursor holds a
     * native sqlite3_stmt through its subclasses, so a leaked cursor that is
     * never finalized leaks that statement for the life of the process.
     */
    @Override
    protected void finalize() {
        if (mSelfObserver != null && mSelfObserverRegistered && mContentResolver != null) {
            try { mContentResolver.unregisterContentObserver(mSelfObserver); }
            catch (Exception ignored) {}
        }
        try {
            if (!mClosed) close();
        } catch (Exception ignored) {}
    }

    /**
     * @deprecated Always returns null. AOSP's own body is `return null;` --
     * the "updated field" mechanism (a per-row overlay of uncommitted edits)
     * was removed from AbstractCursor long ago, and the two methods survive
     * only because subclasses override them.
     */
    @Deprecated
    protected Object getUpdatedField(int columnIndex) {
        return null;
    }

    /** @deprecated Always returns false. See {@link #getUpdatedField}. */
    @Deprecated
    protected boolean isFieldUpdated(int columnIndex) {
        return false;
    }

    @Override public void close() {
        mClosed = true;
        mContentObservable.unregisterAll();
        onDeactivateOrClose();
    }

    /** AOSP: the shared tail of deactivate() and close() -- drop the self
     *  observer from the resolver and tell data-set observers the rows are
     *  no longer valid so adapters release their view state. */
    protected void onDeactivateOrClose() {
        if (mSelfObserver != null && mContentResolver != null) {
            mContentResolver.unregisterContentObserver(mSelfObserver);
            mSelfObserverRegistered = false;
        }
        mDataSetObservable.notifyInvalidated();
    }

    @Override public void deactivate() { onDeactivateOrClose(); }

    /** AOSP AbstractCursor.requery(): re-arms the self observer if deactivate()
     *  dropped it and notifies data-set observers. It deliberately does NOT
     *  re-run the query -- subclasses that own a statement override this. */
    @Override public boolean requery() {
        if (mSelfObserver != null && !mSelfObserverRegistered && mNotifyUris != null
                && mContentResolver != null) {
            final int size = mNotifyUris.size();
            for (int i = 0; i < size; ++i) {
                mContentResolver.registerContentObserver(mNotifyUris.get(i), true, mSelfObserver);
            }
            mSelfObserverRegistered = true;
        }
        mDataSetObservable.notifyChanged();
        return true;
    }

    @Override public void registerContentObserver(ContentObserver observer) {
        mContentObservable.registerObserver(observer);
    }
    @Override public void unregisterContentObserver(ContentObserver observer) {
        // AOSP guards on mClosed: close() already unregisterAll()'d, so a late
        // unregister must not hit Observable's "not registered" IllegalStateException.
        if (!mClosed) {
            mContentObservable.unregisterObserver(observer);
        }
    }
    @Override public void registerDataSetObserver(DataSetObserver observer) {
        mDataSetObservable.registerObserver(observer);
    }
    @Override public void unregisterDataSetObserver(DataSetObserver observer) {
        mDataSetObservable.unregisterObserver(observer);
    }

    @Override public void setNotificationUri(ContentResolver cr, Uri notifyUri) {
        setNotificationUris(cr, Arrays.asList(notifyUri));
    }

    public void setNotificationUris(ContentResolver cr, List<Uri> notifyUris) {
        synchronized (mSelfObserverLock) {
            mNotifyUris = notifyUris;
            mNotifyUri = mNotifyUris.get(0);
            mContentResolver = cr;
            if (mSelfObserver != null) {
                mContentResolver.unregisterContentObserver(mSelfObserver);
                mSelfObserverRegistered = false;
            }
            mSelfObserver = new SelfContentObserver(this);
            final int size = mNotifyUris.size();
            for (int i = 0; i < size; ++i) {
                mContentResolver.registerContentObserver(mNotifyUris.get(i), true, mSelfObserver);
            }
            mSelfObserverRegistered = true;
        }
    }

    @Override public Uri getNotificationUri() {
        synchronized (mSelfObserverLock) { return mNotifyUri; }
    }

    public List<Uri> getNotificationUris() {
        synchronized (mSelfObserverLock) { return mNotifyUris; }
    }

    /** AOSP: called when the backing data changed. selfChange=true means the
     *  change originated from this cursor, in which case AOSP also re-broadcasts
     *  it to the resolver so other observers of the same URI see it. */
    protected void onChange(boolean selfChange) {
        synchronized (mSelfObserverLock) {
            mContentObservable.dispatchChange(selfChange, null);
            if (mNotifyUris != null && selfChange && mContentResolver != null) {
                final int size = mNotifyUris.size();
                for (int i = 0; i < size; ++i) {
                    mContentResolver.notifyChange(mNotifyUris.get(i), mSelfObserver);
                }
            }
        }
    }

    /** AOSP hook subclasses call after mutating their row set. */
    protected void notifyDataSetChange() { mDataSetObservable.notifyChanged(); }

    /** AOSP accessor so subclasses can notify invalidation themselves. */
    protected DataSetObservable getDataSetObservable() { return mDataSetObservable; }

    /** AOSP's self observer: a weakly-held bridge from the ContentResolver's
     *  URI-keyed registry back into this cursor's onChange, so the cursor does
     *  not keep itself alive through the resolver's observer list. */
    protected static class SelfContentObserver extends ContentObserver {
        WeakReference<AbstractCursor> mCursor;

        public SelfContentObserver(AbstractCursor cursor) {
            super(null);
            mCursor = new WeakReference<AbstractCursor>(cursor);
        }

        @Override public boolean deliverSelfNotifications() { return false; }

        @Override public void onChange(boolean selfChange) {
            AbstractCursor cursor = mCursor.get();
            if (cursor != null) {
                cursor.onChange(false);
            }
        }
    }
}
