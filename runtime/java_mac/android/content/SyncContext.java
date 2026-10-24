// android.content.SyncContext -- handed to a SyncAdapter so it can report
// progress and completion back to the SyncManager.
//
// AOSP ref: frameworks/base/core/java/android/content/SyncContext.java
// (android16-release). AOSP's constructor takes the @hide ISyncContext binder
// and every method forwards over it; the two PUBLIC methods are the two below.
// Mac.apk runs no SyncManager -- ContentResolver.requestSync() has no service
// to hand a request to -- so nothing ever constructs one of these and there is
// no binder to forward to. The type exists because an app's SyncAdapter
// subclass NAMES it in its own method signatures, which is a link-time
// requirement independent of whether a sync ever runs.
package android.content;

import android.os.IBinder;

public class SyncContext {
    private IBinder mSyncContextBinder;

    /** @hide upstream. */
    public SyncContext(IBinder syncContextBinder) {
        mSyncContextBinder = syncContextBinder;
    }

    /** AOSP: the binder the SyncManager listens on. Null here -- there is no
     *  SyncManager, and a fabricated binder would accept calls that go
     *  nowhere. */
    public IBinder getSyncContextBinder() { return mSyncContextBinder; }

    /** AOSP forwards the result over the binder. With no binder there is
     *  nothing to notify; the sync's own return value is the only channel the
     *  caller has here. */
    public void onFinished(SyncResult result) { }
}
