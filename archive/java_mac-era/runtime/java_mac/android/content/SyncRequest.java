// android.content.SyncRequest -- an immutable, fully-specified sync request
// built by SyncRequest.Builder and handed to
// ContentResolver.requestSync(SyncRequest).
//
// AOSP ref: frameworks/base/core/java/android/content/SyncRequest.java
// (android16-release). Ported verbatim, including EVERY Builder precondition:
// those exceptions are the class's whole observable behaviour before a sync
// runs, and an app that relies on one (e.g. calling syncOnce() twice) must see
// the same IllegalArgumentException here as on a device.
//
// Note that Mac.apk has no SyncManager, so a built request is never EXECUTED
// (see ContentResolver.requestSync). Building one still has to work: the
// object is constructed, validated and inspected entirely within the app.
package android.content;

import android.accounts.Account;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class SyncRequest implements Parcelable {
    private final Account mAccountToSync;
    private final String mAuthority;
    private final Bundle mExtras;
    private final boolean mDisallowMetered;
    private final long mSyncFlexTimeSecs;
    private final long mSyncRunTimeSecs;
    private final boolean mIsPeriodic;
    private final boolean mIsAuthority;
    private final boolean mIsExpedited;
    private final boolean mIsScheduledAsExpeditedJob;

    // All of these are @hide upstream but public in bytecode.
    public boolean isPeriodic() { return mIsPeriodic; }
    public boolean isExpedited() { return mIsExpedited; }
    public boolean isScheduledAsExpeditedJob() { return mIsScheduledAsExpeditedJob; }
    public Account getAccount() { return mAccountToSync; }
    public String getProvider() { return mAuthority; }
    public Bundle getBundle() { return mExtras; }
    public long getSyncFlexTime() { return mSyncFlexTimeSecs; }
    public long getSyncRunTime() { return mSyncRunTimeSecs; }

    public static final Creator<SyncRequest> CREATOR = new Creator<SyncRequest>() {
        @Override public SyncRequest createFromParcel(Parcel in) { return new SyncRequest(in); }
        @Override public SyncRequest[] newArray(int size) { return new SyncRequest[size]; }
    };

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeBundle(mExtras);
        parcel.writeLong(mSyncFlexTimeSecs);
        parcel.writeLong(mSyncRunTimeSecs);
        parcel.writeInt(mIsPeriodic ? 1 : 0);
        parcel.writeInt(mDisallowMetered ? 1 : 0);
        parcel.writeInt(mIsAuthority ? 1 : 0);
        parcel.writeInt(mIsExpedited ? 1 : 0);
        parcel.writeInt(mIsScheduledAsExpeditedJob ? 1 : 0);
        parcel.writeParcelable(mAccountToSync, flags);
        parcel.writeString(mAuthority);
    }

    private SyncRequest(Parcel in) {
        mExtras = in.readBundle();
        mSyncFlexTimeSecs = in.readLong();
        mSyncRunTimeSecs = in.readLong();
        mIsPeriodic = (in.readInt() != 0);
        mDisallowMetered = (in.readInt() != 0);
        mIsAuthority = (in.readInt() != 0);
        mIsExpedited = (in.readInt() != 0);
        mIsScheduledAsExpeditedJob = (in.readInt() != 0);
        mAccountToSync = in.readParcelable(null, Account.class);
        mAuthority = in.readString();
    }

    protected SyncRequest(SyncRequest.Builder b) {
        mSyncFlexTimeSecs = b.mSyncFlexTimeSecs;
        mSyncRunTimeSecs = b.mSyncRunTimeSecs;
        mAccountToSync = b.mAccount;
        mAuthority = b.mAuthority;
        mIsPeriodic = (b.mSyncType == Builder.SYNC_TYPE_PERIODIC);
        mIsAuthority = (b.mSyncTarget == Builder.SYNC_TARGET_ADAPTER);
        mIsExpedited = b.mExpedited;
        mIsScheduledAsExpeditedJob = b.mScheduleAsExpeditedJob;
        mExtras = new Bundle(b.mCustomExtras);
        mExtras.putAll(b.mSyncConfigExtras);
        mDisallowMetered = b.mDisallowMetered;
    }

    public static class Builder {
        private static final int SYNC_TYPE_UNKNOWN = 0;
        private static final int SYNC_TYPE_PERIODIC = 1;
        private static final int SYNC_TYPE_ONCE = 2;
        private static final int SYNC_TARGET_UNKNOWN = 0;
        private static final int SYNC_TARGET_ADAPTER = 2;

        private long mSyncFlexTimeSecs;
        private long mSyncRunTimeSecs;
        private Bundle mCustomExtras;
        private Bundle mSyncConfigExtras;
        private boolean mDisallowMetered;
        private int mSyncType = SYNC_TYPE_UNKNOWN;
        private int mSyncTarget = SYNC_TARGET_UNKNOWN;
        private boolean mIsManual;
        private boolean mNoRetry;
        private boolean mIgnoreBackoff;
        private boolean mIgnoreSettings;
        private boolean mExpedited;
        private Account mAccount;
        private String mAuthority;
        private boolean mRequiresCharging;
        private boolean mScheduleAsExpeditedJob;

        public Builder() {
        }

        public Builder syncOnce() {
            if (mSyncType != SYNC_TYPE_UNKNOWN) {
                throw new IllegalArgumentException("Sync type has already been defined.");
            }
            mSyncType = SYNC_TYPE_ONCE;
            setupInterval(0, 0);
            return this;
        }

        public Builder syncPeriodic(long pollFrequency, long beforeSeconds) {
            if (mSyncType != SYNC_TYPE_UNKNOWN) {
                throw new IllegalArgumentException("Sync type has already been defined.");
            }
            mSyncType = SYNC_TYPE_PERIODIC;
            setupInterval(pollFrequency, beforeSeconds);
            return this;
        }

        private void setupInterval(long at, long before) {
            if (before > at) {
                throw new IllegalArgumentException("Specified run time for the sync must be"
                        + " after the specified flex time.");
            }
            mSyncRunTimeSecs = at;
            mSyncFlexTimeSecs = before;
        }

        public Builder setDisallowMetered(boolean disallow) {
            if (mIgnoreSettings && disallow) {
                throw new IllegalArgumentException("setDisallowMetered(true) after having"
                        + " specified that settings are ignored.");
            }
            mDisallowMetered = disallow;
            return this;
        }

        public Builder setRequiresCharging(boolean requiresCharging) {
            mRequiresCharging = requiresCharging;
            return this;
        }

        public Builder setSyncAdapter(Account account, String authority) {
            if (mSyncTarget != SYNC_TARGET_UNKNOWN) {
                throw new IllegalArgumentException("Sync target has already been defined.");
            }
            if (authority != null && authority.length() == 0) {
                throw new IllegalArgumentException("Authority must be non-empty");
            }
            mSyncTarget = SYNC_TARGET_ADAPTER;
            mAccount = account;
            mAuthority = authority;
            return this;
        }

        public Builder setExtras(Bundle bundle) {
            mCustomExtras = bundle;
            return this;
        }

        public Builder setNoRetry(boolean noRetry) {
            mNoRetry = noRetry;
            return this;
        }

        public Builder setIgnoreSettings(boolean ignoreSettings) {
            if (mDisallowMetered && ignoreSettings) {
                throw new IllegalArgumentException("setIgnoreSettings(true) after having specified"
                        + " sync settings with this builder.");
            }
            mIgnoreSettings = ignoreSettings;
            return this;
        }

        public Builder setIgnoreBackoff(boolean ignoreBackoff) {
            mIgnoreBackoff = ignoreBackoff;
            return this;
        }

        public Builder setManual(boolean isManual) {
            mIsManual = isManual;
            return this;
        }

        public Builder setExpedited(boolean expedited) {
            mExpedited = expedited;
            return this;
        }

        public Builder setScheduleAsExpeditedJob(boolean scheduleAsExpeditedJob) {
            mScheduleAsExpeditedJob = scheduleAsExpeditedJob;
            return this;
        }

        public SyncRequest build() {
            mSyncConfigExtras = new Bundle();
            if (mIgnoreBackoff) {
                mSyncConfigExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true);
            }
            if (mDisallowMetered) {
                mSyncConfigExtras.putBoolean(ContentResolver.SYNC_EXTRAS_DISALLOW_METERED, true);
            }
            if (mRequiresCharging) {
                mSyncConfigExtras.putBoolean(ContentResolver.SYNC_EXTRAS_REQUIRE_CHARGING, true);
            }
            if (mIgnoreSettings) {
                mSyncConfigExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);
            }
            if (mNoRetry) {
                mSyncConfigExtras.putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY, true);
            }
            if (mExpedited) {
                mSyncConfigExtras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
            }
            if (mScheduleAsExpeditedJob) {
                mSyncConfigExtras.putBoolean(
                        ContentResolver.SYNC_EXTRAS_SCHEDULE_AS_EXPEDITED_JOB, true);
            }
            if (mIsManual) {
                mSyncConfigExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true);
                mSyncConfigExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);
            }
            if (mCustomExtras == null) {
                mCustomExtras = new Bundle();
            }
            ContentResolver.validateSyncExtrasBundle(mCustomExtras);
            if (mSyncType == SYNC_TYPE_PERIODIC) {
                if (ContentResolver.invalidPeriodicExtras(mCustomExtras)
                        || ContentResolver.invalidPeriodicExtras(mSyncConfigExtras)) {
                    throw new IllegalArgumentException("Illegal extras were set");
                }
            }
            if (mCustomExtras.getBoolean(ContentResolver.SYNC_EXTRAS_SCHEDULE_AS_EXPEDITED_JOB)
                    || mScheduleAsExpeditedJob) {
                if (ContentResolver.hasInvalidScheduleAsEjExtras(mCustomExtras)
                        || ContentResolver.hasInvalidScheduleAsEjExtras(mSyncConfigExtras)) {
                    throw new IllegalArgumentException("Illegal extras were set");
                }
            }
            if (mSyncTarget == SYNC_TARGET_UNKNOWN) {
                throw new IllegalArgumentException("Must specify an adapter with"
                        + " setSyncAdapter(Account, String");
            }
            return new SyncRequest(this);
        }
    }
}
