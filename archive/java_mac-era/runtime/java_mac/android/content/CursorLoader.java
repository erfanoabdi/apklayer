// android.content.CursorLoader -- the canonical AsyncTaskLoader that
// runs a ContentResolver.query() off the main thread and delivers the
// resulting Cursor, re-querying when the provider notifies a change on
// the watched URI. Legacy LoaderManager apps use it to back ListViews /
// RecyclerViews from a ContentProvider.
//
// Now that ContentResolver dispatches through ContentProviderRegistry,
// CursorLoader returns a real Cursor for any content:// URI an
// in-process provider serves (null otherwise, which the consuming
// CursorAdapter handles as "empty").
//
// AOSP ref:
//   frameworks/base/core/java/android/content/CursorLoader.java
package android.content;

import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;

public class CursorLoader extends AsyncTaskLoader<Cursor> {
    final Loader<Cursor>.ForceLoadContentObserver mObserver;

    Uri mUri;
    String[] mProjection;
    String mSelection;
    String[] mSelectionArgs;
    String mSortOrder;

    Cursor mCursor;
    CancellationSignal mCancellationSignal;

    public CursorLoader(Context context) {
        super(context);
        mObserver = new ForceLoadContentObserver();
    }

    public CursorLoader(Context context, Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        super(context);
        mObserver = new ForceLoadContentObserver();
        mUri = uri;
        mProjection = projection;
        mSelection = selection;
        mSelectionArgs = selectionArgs;
        mSortOrder = sortOrder;
    }

    @Override public Cursor loadInBackground() {
        synchronized (this) {
            if (isLoadInBackgroundCanceled()) {
                throw new OperationCanceledException();
            }
            mCancellationSignal = new CancellationSignal();
        }
        try {
            ContentResolver cr = getContext() != null ? getContext().getContentResolver() : null;
            Cursor cursor = cr == null ? null : cr.query(mUri, mProjection, mSelection,
                    mSelectionArgs, mSortOrder, mCancellationSignal);
            if (cursor != null) {
                try {
                    // Force the cursor to fully fetch (AOSP calls getCount())
                    // and register our observer so notifyChange re-loads.
                    cursor.getCount();
                    cursor.registerContentObserver(mObserver);
                } catch (RuntimeException ex) {
                    cursor.close();
                    throw ex;
                }
            }
            return cursor;
        } finally {
            synchronized (this) {
                mCancellationSignal = null;
            }
        }
    }

    @Override public void cancelLoadInBackground() {
        super.cancelLoadInBackground();
        synchronized (this) {
            if (mCancellationSignal != null) {
                mCancellationSignal.cancel();
            }
        }
    }

    @Override public void deliverResult(Cursor cursor) {
        if (isReset()) {
            // An async query came in while the loader is stopped/reset.
            if (cursor != null) cursor.close();
            return;
        }
        Cursor oldCursor = mCursor;
        mCursor = cursor;
        if (isStarted()) {
            super.deliverResult(cursor);
        }
        if (oldCursor != null && oldCursor != cursor && !oldCursor.isClosed()) {
            oldCursor.close();
        }
    }

    @Override protected void onStartLoading() {
        if (mCursor != null) {
            deliverResult(mCursor);
        }
        if (takeContentChanged() || mCursor == null) {
            forceLoad();
        }
    }

    @Override protected void onStopLoading() {
        cancelLoad();
    }

    @Override public void onCanceled(Cursor cursor) {
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
    }

    @Override protected void onReset() {
        super.onReset();
        // Ensure the loader is stopped.
        onStopLoading();
        if (mCursor != null && !mCursor.isClosed()) {
            mCursor.close();
        }
        mCursor = null;
    }

    public Uri getUri() { return mUri; }
    public void setUri(Uri uri) { mUri = uri; }

    public String[] getProjection() { return mProjection; }
    public void setProjection(String[] projection) { mProjection = projection; }

    public String getSelection() { return mSelection; }
    public void setSelection(String selection) { mSelection = selection; }

    public String[] getSelectionArgs() { return mSelectionArgs; }
    public void setSelectionArgs(String[] selectionArgs) { mSelectionArgs = selectionArgs; }

    public String getSortOrder() { return mSortOrder; }
    public void setSortOrder(String sortOrder) { mSortOrder = sortOrder; }

    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.print(prefix); writer.print("mUri="); writer.println(mUri);
        writer.print(prefix); writer.print("mProjection=");
        writer.println(Arrays.toString(mProjection));
        writer.print(prefix); writer.print("mSelection="); writer.println(mSelection);
        writer.print(prefix); writer.print("mSelectionArgs=");
        writer.println(Arrays.toString(mSelectionArgs));
        writer.print(prefix); writer.print("mSortOrder="); writer.println(mSortOrder);
        writer.print(prefix); writer.print("mCursor="); writer.println(mCursor);
    }
}
