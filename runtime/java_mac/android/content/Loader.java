// android.content.Loader<D> -- the base class of the (deprecated but
// still widely shipped) framework Loader API. Legacy apps that use
// LoaderManager + CursorLoader to populate ListViews from a
// ContentProvider extend / instantiate this hierarchy. We mirror AOSP's
// observable contract: start/stop/reset transitions, the
// OnLoadCompleteListener callback, content-change observation via
// ForceLoadContentObserver, and the registerListener bookkeeping.
//
// AOSP ref:
//   frameworks/base/core/java/android/content/Loader.java
package android.content;

import android.database.ContentObserver;
import android.os.Handler;

public class Loader<D> {
    int mId;
    OnLoadCompleteListener<D> mListener;
    OnLoadCanceledListener<D> mOnLoadCanceledListener;
    Context mContext;
    boolean mStarted = false;
    boolean mAbandoned = false;
    boolean mReset = true;
    boolean mContentChanged = false;
    boolean mProcessingChange = false;

    /** AOSP nested class: a ContentObserver that, when the watched URI
     *  changes, marks the loader content-changed and force-reloads if
     *  started. Subclasses (CursorLoader) register one of these on the
     *  query URI so the list auto-refreshes on notifyChange. */
    public final class ForceLoadContentObserver extends ContentObserver {
        public ForceLoadContentObserver() {
            super(new Handler());
        }

        @Override public boolean deliverSelfNotifications() { return true; }

        @Override public void onChange(boolean selfChange) {
            onContentChanged();
        }
    }

    /** AOSP callback fired when a load completes. */
    public interface OnLoadCompleteListener<D> {
        void onLoadComplete(Loader<D> loader, D data);
    }

    /** AOSP callback fired when a load is canceled (API-18). */
    public interface OnLoadCanceledListener<D> {
        void onLoadCanceled(Loader<D> loader);
    }

    public Loader(Context context) {
        mContext = context != null ? context.getApplicationContext() : null;
    }

    /** Deliver a result to the registered completion listener. */
    public void deliverResult(D data) {
        if (mListener != null) {
            mListener.onLoadComplete(this, data);
        }
    }

    public void deliverCancellation() {
        if (mOnLoadCanceledListener != null) {
            mOnLoadCanceledListener.onLoadCanceled(this);
        }
    }

    public Context getContext() { return mContext; }
    public int getId() { return mId; }

    public void registerListener(int id, OnLoadCompleteListener<D> listener) {
        if (mListener != null) {
            throw new IllegalStateException("There is already a listener registered");
        }
        mListener = listener;
        mId = id;
    }

    public void unregisterListener(OnLoadCompleteListener<D> listener) {
        if (mListener == null) {
            throw new IllegalStateException("No listener register");
        }
        if (mListener != listener) {
            throw new IllegalArgumentException("Attempting to unregister the wrong listener");
        }
        mListener = null;
    }

    public void registerOnLoadCanceledListener(OnLoadCanceledListener<D> listener) {
        if (mOnLoadCanceledListener != null) {
            throw new IllegalStateException("There is already a listener registered");
        }
        mOnLoadCanceledListener = listener;
    }

    public void unregisterOnLoadCanceledListener(OnLoadCanceledListener<D> listener) {
        if (mOnLoadCanceledListener == null) {
            throw new IllegalStateException("No listener register");
        }
        if (mOnLoadCanceledListener != listener) {
            throw new IllegalArgumentException("Attempting to unregister the wrong listener");
        }
        mOnLoadCanceledListener = null;
    }

    public boolean isStarted()   { return mStarted; }
    public boolean isAbandoned() { return mAbandoned; }
    public boolean isReset()     { return mReset; }

    public final void startLoading() {
        mStarted = true;
        mReset = false;
        mAbandoned = false;
        onStartLoading();
    }

    protected void onStartLoading() {}

    public boolean cancelLoad() {
        return onCancelLoad();
    }

    protected boolean onCancelLoad() { return false; }

    public void forceLoad() { onForceLoad(); }

    protected void onForceLoad() {}

    public void stopLoading() {
        mStarted = false;
        onStopLoading();
    }

    protected void onStopLoading() {}

    public void abandon() {
        mAbandoned = true;
        onAbandon();
    }

    protected void onAbandon() {}

    public final void reset() {
        onReset();
        mReset = true;
        mStarted = false;
        mAbandoned = false;
        mContentChanged = false;
        mProcessingChange = false;
    }

    protected void onReset() {}

    public boolean takeContentChanged() {
        boolean res = mContentChanged;
        mContentChanged = false;
        mProcessingChange |= res;
        return res;
    }

    public void commitContentChanged() {
        mProcessingChange = false;
    }

    public void rollbackContentChanged() {
        if (mProcessingChange) {
            mContentChanged = true;
        }
    }

    public void onContentChanged() {
        if (mStarted) {
            forceLoad();
        } else {
            mContentChanged = true;
        }
    }

    /** AOSP core/java/android/content/Loader.java:552 -- the debug dump a
     *  LoaderManager prints for each loader. Ported verbatim, including which
     *  lines are conditional on the loader being in a non-default state. */
    public void dump(String prefix, java.io.FileDescriptor fd, java.io.PrintWriter writer,
                     String[] args) {
        writer.print(prefix); writer.print("mId="); writer.print(mId);
        writer.print(" mListener="); writer.println(mListener);
        if (mStarted || mContentChanged || mProcessingChange) {
            writer.print(prefix); writer.print("mStarted="); writer.print(mStarted);
            writer.print(" mContentChanged="); writer.print(mContentChanged);
            writer.print(" mProcessingChange="); writer.println(mProcessingChange);
        }
        if (mAbandoned || mReset) {
            writer.print(prefix); writer.print("mAbandoned="); writer.print(mAbandoned);
            writer.print(" mReset="); writer.println(mReset);
        }
    }

    public String dataToString(D data) {
        StringBuilder sb = new StringBuilder(64);
        sb.append(data);
        return sb.toString();
    }
}
