// android.content.AbstractThreadedSyncAdapter -- the base class an app extends
// to implement a sync adapter, and the class its SyncService returns a binder
// for from onBind().
//
// Why Mac.apk needs it: Telegram and Signal both subclass it. A missing
// superclass is a link failure at CLASS LOAD, so the app's whole sync-service
// class fails to define -- not merely "sync does not run", but a
// NoClassDefFoundError the first time anything touches that class.
//
// AOSP source mirrored: frameworks/base/core/java/android/content/
// AbstractThreadedSyncAdapter.java (android11-release). Every PUBLIC member is
// reproduced exactly: both constructors, getContext, getSyncAdapterBinder,
// onUnsyncableAccount, the abstract onPerformSync, onSecurityException, and
// both onSyncCanceled overloads, plus the deprecated LOG_SYNC_DETAILS
// constant.
//
// ---------------------------------------------------------------------------
// WHAT THIS DEVICE HONESTLY CANNOT DO, and how that is expressed here
// ---------------------------------------------------------------------------
// On real Android the SyncManager -- a system_server component -- decides WHEN
// to sync, binds to the app's SyncService, and calls startSync over the
// ISyncAdapter AIDL interface. Mac.apk has no SyncManager and no binder
// transport, so nothing ever requests a sync. That is not a broken state: it
// is exactly what a real device looks like when no account of the adapter's
// accountType has been added, which is a case every sync adapter already has
// to tolerate.
//
// So this class is deliberately NOT pretending. It does not invent a scheduler
// and it does not call onPerformSync at boot to look busy -- that would be the
// workaround-shaped lie the no-shortcuts rule exists to prevent, and an app
// would sync against a server on a schedule nobody asked for.
//
// What IS real here: the threading machinery. mSyncThreads, toSyncKey, the
// SyncThread body, the provider acquire/release around onPerformSync, and both
// onSyncCanceled overloads behave exactly as AOSP specifies, driven through the
// package-private startSync() seam below. That seam is the local stand-in for
// ISyncAdapter.Stub.startSync; when a sync driver lands in this package
// (ContentResolver.requestSync is the natural home) it calls startSync and the
// adapter runs for real, with no change to this file.
package android.content;

import android.accounts.Account;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractThreadedSyncAdapter {
    private static final String TAG = "SyncAdapter";

    /**
     * Kernel event log tag. Also listed in data/etc/event-log-tags.
     * @deprecated Private constant. May go away in the next release.
     */
    @Deprecated
    public static final int LOG_SYNC_DETAILS = 2743;

    private final Context mContext;
    private final AtomicInteger mNumSyncStarts;

    // The binder an app's SyncService hands back from onBind(). AOSP returns
    // an ISyncAdapter.Stub; this tree has no AIDL stub and no remote caller, so
    // it is a plain Binder. It is deliberately non-null: returning null from
    // Service.onBind is a DIFFERENT and observable contract (the bind fails
    // rather than succeeding with an idle adapter), and an app that null-checks
    // its own onBind result would take the wrong branch.
    private final Binder mSyncAdapterBinder = new Binder();

    // All accesses to this member must be synchronized on mSyncThreadLock.
    private final HashMap<Account, SyncThread> mSyncThreads = new HashMap<Account, SyncThread>();
    private final Object mSyncThreadLock = new Object();

    private final boolean mAutoInitialize;
    private boolean mAllowParallelSyncs;

    /**
     * @param context the Context this is running within
     * @param autoInitialize if true, sync requests carrying
     *        ContentResolver.SYNC_EXTRAS_INITIALIZE are handled internally by
     *        marking the authority syncable instead of reaching onPerformSync
     */
    public AbstractThreadedSyncAdapter(Context context, boolean autoInitialize) {
        this(context, autoInitialize, false /* allowParallelSyncs */);
    }

    /**
     * @param context the Context this is running within
     * @param autoInitialize see the two-arg constructor
     * @param allowParallelSyncs if true, syncs for different accounts may run
     *        at the same time, each on its own thread. Must match the
     *        android:allowParallelSyncs setting in the adapter's XML.
     */
    public AbstractThreadedSyncAdapter(Context context,
            boolean autoInitialize, boolean allowParallelSyncs) {
        mContext = context;
        mNumSyncStarts = new AtomicInteger(0);
        mAutoInitialize = autoInitialize;
        mAllowParallelSyncs = allowParallelSyncs;
    }

    public Context getContext() {
        return mContext;
    }

    // When parallel syncs are off, every account shares the single null key, so
    // a second request while one is running is reported as already-in-progress.
    private Account toSyncKey(Account account) {
        if (mAllowParallelSyncs) {
            return account;
        } else {
            return null;
        }
    }

    /**
     * Starts a sync on a background thread, mirroring AOSP's
     * ISyncAdapter.Stub.startSync (AbstractThreadedSyncAdapter.java:181-241).
     * Package-private because the only legitimate caller is a sync driver
     * living in android.content; an app must never reach past the framework to
     * start its own sync, exactly as on real Android where this is a binder
     * entry point the app does not hold.
     *
     * Returns true if a sync thread was started, false if one was already
     * running for this key (AOSP answers that case with
     * SyncResult.ALREADY_IN_PROGRESS over the binder).
     *
     * NOTE: AOSP's mAutoInitialize fast path is not reproduced -- it needs
     * ContentResolver.getIsSyncable / setIsSyncable, which this tree does not
     * provide. mAutoInitialize is still stored so the behaviour can be added
     * without touching this class's shape.
     */
    boolean startSync(String authority, Account account, Bundle extras) {
        final Account threadsKey = toSyncKey(account);
        synchronized (mSyncThreadLock) {
            if (mSyncThreads.containsKey(threadsKey)) {
                return false;
            }
            SyncThread syncThread = new SyncThread(
                    "SyncAdapterThread-" + mNumSyncStarts.incrementAndGet(),
                    authority, account, extras);
            mSyncThreads.put(threadsKey, syncThread);
            syncThread.start();
            return true;
        }
    }

    /**
     * The thread that invokes onPerformSync. It acquires the provider for this
     * sync before calling onPerformSync and releases it afterwards. Interrupt
     * this thread to cancel the sync.
     */
    private class SyncThread extends Thread {
        private final String mAuthority;
        private final Account mAccount;
        private final Bundle mExtras;
        private final Account mThreadsKey;

        private SyncThread(String name, String authority, Account account, Bundle extras) {
            super(name);
            mAuthority = authority;
            mAccount = account;
            mExtras = extras;
            mThreadsKey = toSyncKey(account);
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            SyncResult syncResult = new SyncResult();
            ContentProviderClient provider = null;
            try {
                if (isCanceled()) {
                    return;
                }

                provider = mContext.getContentResolver()
                        .acquireContentProviderClient(mAuthority);
                if (provider != null) {
                    AbstractThreadedSyncAdapter.this.onPerformSync(mAccount, mExtras,
                            mAuthority, provider, syncResult);
                } else {
                    syncResult.databaseError = true;
                }
            } catch (SecurityException e) {
                AbstractThreadedSyncAdapter.this.onSecurityException(mAccount, mExtras,
                        mAuthority, syncResult);
                syncResult.databaseError = true;
            } finally {
                if (provider != null) {
                    provider.release();
                }
                // AOSP reports syncResult back to the SyncManager here via
                // SyncContext.onFinished. There is no SyncManager on this
                // device, so there is nobody to report to; log it at debug so a
                // driven sync is at least observable in the runtime log.
                if (!isCanceled()) {
                    Log.d(TAG, "sync finished " + mAuthority + " " + syncResult);
                }
                // Synchronize so the removal is seen by other threads that also
                // synchronize accesses to mSyncThreads.
                synchronized (mSyncThreadLock) {
                    mSyncThreads.remove(mThreadsKey);
                }
            }
        }

        private boolean isCanceled() {
            return Thread.currentThread().isInterrupted();
        }
    }

    /** @return a reference to the IBinder of the SyncAdapter service. */
    public final IBinder getSyncAdapterBinder() {
        return mSyncAdapterBinder.asBinder();
    }

    /**
     * Allows an adapter to defer syncing until all accounts are properly set
     * up. Returning false defers the sync; the default returns true, i.e.
     * syncing starts immediately.
     */
    public boolean onUnsyncableAccount() {
        return true;
    }

    /**
     * Perform a sync for this account. Invocations are serialized unless the
     * adapter declared android:allowParallelSyncs.
     *
     * @param account the account that should be synced
     * @param extras SyncAdapter-specific parameters, guaranteed non-null
     * @param authority the authority of this sync request
     * @param provider a ContentProviderClient for that authority
     * @param syncResult the result the adapter fills in
     */
    public abstract void onPerformSync(Account account, Bundle extras,
            String authority, ContentProviderClient provider, SyncResult syncResult);

    /**
     * Report that a SecurityException was raised opening the content provider
     * before onPerformSync. Treated as a sync database failure.
     */
    public void onSecurityException(Account account, Bundle extras,
            String authority, SyncResult syncResult) {
    }

    /**
     * A sync operation has been canceled. Invoked on a thread other than the
     * sync thread. Only called for adapters that do NOT support parallel syncs.
     */
    public void onSyncCanceled() {
        final SyncThread syncThread;
        synchronized (mSyncThreadLock) {
            syncThread = mSyncThreads.get(null);
        }
        if (syncThread != null) {
            syncThread.interrupt();
        }
    }

    /**
     * A sync operation has been canceled. Invoked on a thread other than the
     * sync thread. Only called for adapters that DO support parallel syncs.
     *
     * @param thread the Thread of the sync that is to be canceled
     */
    public void onSyncCanceled(Thread thread) {
        thread.interrupt();
    }
}
