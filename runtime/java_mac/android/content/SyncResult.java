// android.content.SyncResult -- how a SyncAdapter reports the disposition of
// one sync back to the caller. On real Android the SyncManager reads these
// fields to decide whether to retry, back off, or give up.
//
// Why Mac.apk needs it: Telegram and Signal reference it, and it is the fifth
// parameter of AbstractThreadedSyncAdapter.onPerformSync, so an app cannot
// even declare its sync adapter subclass without it.
//
// AOSP source mirrored: frameworks/base/core/java/android/content/
// SyncResult.java (android11-release), field-for-field including the
// ALREADY_IN_PROGRESS singleton and its clear() guard.
//
// This is a complete implementation: SyncResult is pure state plus derived
// predicates (hasHardError / hasSoftError / madeSomeProgress), with no system
// dependency. What Mac.apk lacks is a SyncManager to READ the result -- see
// AbstractThreadedSyncAdapter for that half.
package android.content;

import android.os.Parcel;
import android.os.Parcelable;

public final class SyncResult implements Parcelable {
    /** The SyncAdapter was already performing a sync and could not process
     *  this request; the caller should reschedule it. */
    public final boolean syncAlreadyInProgress;

    /** The adapter would have to issue too many deletes to the server to
     *  satisfy the request. */
    public boolean tooManyDeletions;

    /** Hard error: the same operation was retried too many times. */
    public boolean tooManyRetries;

    /** Hard error: the storage layer returned an error. */
    public boolean databaseError;

    /** Request an immediate follow-up sync with the same account/authority
     *  and an empty extras Bundle. */
    public boolean fullSyncRequested;

    /** Ignored by the SyncManager. */
    public boolean partialSyncUnavailable;

    /** Ignored by the SyncManager. */
    public boolean moreRecordsToGet;

    /** Delay future syncs for this account/authority until this time, in
     *  seconds since the Java epoch. The system's exponential back-off always
     *  wins when it is longer. */
    public long delayUntil;

    /** Counters describing what the sync did, and whether it hard- or
     *  soft-errored. */
    public final SyncStats stats;

    /** Returned by an adapter that is already syncing; the caller reschedules. */
    public static final SyncResult ALREADY_IN_PROGRESS;

    static {
        ALREADY_IN_PROGRESS = new SyncResult(true);
    }

    /** Create a "clean" SyncResult. Returned unmodified, it means the sync
     *  completed successfully. */
    public SyncResult() {
        this(false);
    }

    private SyncResult(boolean syncAlreadyInProgress) {
        this.syncAlreadyInProgress = syncAlreadyInProgress;
        this.tooManyDeletions = false;
        this.tooManyRetries = false;
        this.fullSyncRequested = false;
        this.partialSyncUnavailable = false;
        this.moreRecordsToGet = false;
        this.delayUntil = 0;
        this.stats = new SyncStats();
    }

    private SyncResult(Parcel parcel) {
        syncAlreadyInProgress = parcel.readInt() != 0;
        tooManyDeletions = parcel.readInt() != 0;
        tooManyRetries = parcel.readInt() != 0;
        databaseError = parcel.readInt() != 0;
        fullSyncRequested = parcel.readInt() != 0;
        partialSyncUnavailable = parcel.readInt() != 0;
        moreRecordsToGet = parcel.readInt() != 0;
        delayUntil = parcel.readLong();
        stats = new SyncStats(parcel);
    }

    /** A hard error is not retried (parse / conflict / auth exceptions, too
     *  many deletions, too many retries, or a database error). */
    public boolean hasHardError() {
        return stats.numParseExceptions > 0
                || stats.numConflictDetectedExceptions > 0
                || stats.numAuthExceptions > 0
                || tooManyDeletions
                || tooManyRetries
                || databaseError;
    }

    /** A soft error is retried with exponential back-off (an IO exception, or
     *  a sync already in progress). */
    public boolean hasSoftError() {
        return syncAlreadyInProgress || stats.numIoExceptions > 0;
    }

    /** True if either a soft or a hard error occurred. */
    public boolean hasError() {
        return hasSoftError() || hasHardError();
    }

    /** True if the sync should be rescheduled because it got somewhere. */
    public boolean madeSomeProgress() {
        return ((stats.numDeletes > 0) && !tooManyDeletions)
                || stats.numInserts > 0
                || stats.numUpdates > 0;
    }

    /** Clears the SyncResult to a clean state. Throws
     *  UnsupportedOperationException on the ALREADY_IN_PROGRESS singleton,
     *  which is shared and must stay immutable. */
    public void clear() {
        if (syncAlreadyInProgress) {
            throw new UnsupportedOperationException(
                    "you are not allowed to clear the ALREADY_IN_PROGRESS SyncStats");
        }
        tooManyDeletions = false;
        tooManyRetries = false;
        databaseError = false;
        fullSyncRequested = false;
        partialSyncUnavailable = false;
        moreRecordsToGet = false;
        delayUntil = 0;
        stats.clear();
    }

    public static final Creator<SyncResult> CREATOR = new Creator<SyncResult>() {
        public SyncResult createFromParcel(Parcel in) {
            return new SyncResult(in);
        }

        public SyncResult[] newArray(int size) {
            return new SyncResult[size];
        }
    };

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(syncAlreadyInProgress ? 1 : 0);
        parcel.writeInt(tooManyDeletions ? 1 : 0);
        parcel.writeInt(tooManyRetries ? 1 : 0);
        parcel.writeInt(databaseError ? 1 : 0);
        parcel.writeInt(fullSyncRequested ? 1 : 0);
        parcel.writeInt(partialSyncUnavailable ? 1 : 0);
        parcel.writeInt(moreRecordsToGet ? 1 : 0);
        parcel.writeLong(delayUntil);
        stats.writeToParcel(parcel, flags);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SyncResult:");
        if (syncAlreadyInProgress) {
            sb.append(" syncAlreadyInProgress: ").append(syncAlreadyInProgress);
        }
        if (tooManyDeletions) sb.append(" tooManyDeletions: ").append(tooManyDeletions);
        if (tooManyRetries) sb.append(" tooManyRetries: ").append(tooManyRetries);
        if (databaseError) sb.append(" databaseError: ").append(databaseError);
        if (fullSyncRequested) sb.append(" fullSyncRequested: ").append(fullSyncRequested);
        if (partialSyncUnavailable) {
            sb.append(" partialSyncUnavailable: ").append(partialSyncUnavailable);
        }
        if (moreRecordsToGet) sb.append(" moreRecordsToGet: ").append(moreRecordsToGet);
        if (delayUntil > 0) sb.append(" delayUntil: ").append(delayUntil);
        sb.append(stats);
        return sb.toString();
    }

    /**
     * Debugging string: a sequence of code letters each followed by a count.
     * f - fullSyncRequested, r - partialSyncUnavailable, X - hardError,
     * e - numParseExceptions, c - numConflictDetectedExceptions,
     * a - numAuthExceptions, D - tooManyDeletions, R - tooManyRetries,
     * b - databaseError, x - softError, l - syncAlreadyInProgress,
     * I - numIoExceptions
     */
    public String toDebugString() {
        StringBuffer sb = new StringBuffer();

        if (fullSyncRequested) {
            sb.append("f1");
        }
        if (partialSyncUnavailable) {
            sb.append("r1");
        }
        if (hasHardError()) {
            sb.append("X1");
        }
        if (stats.numParseExceptions > 0) {
            sb.append("e").append(stats.numParseExceptions);
        }
        if (stats.numConflictDetectedExceptions > 0) {
            sb.append("c").append(stats.numConflictDetectedExceptions);
        }
        if (stats.numAuthExceptions > 0) {
            sb.append("a").append(stats.numAuthExceptions);
        }
        if (tooManyDeletions) {
            sb.append("D1");
        }
        if (tooManyRetries) {
            sb.append("R1");
        }
        if (databaseError) {
            sb.append("b1");
        }
        if (hasSoftError()) {
            sb.append("x1");
        }
        if (syncAlreadyInProgress) {
            sb.append("l1");
        }
        if (stats.numIoExceptions > 0) {
            sb.append("I").append(stats.numIoExceptions);
        }
        return sb.toString();
    }
}
