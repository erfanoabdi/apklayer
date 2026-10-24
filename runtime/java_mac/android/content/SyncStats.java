// android.content.SyncStats -- the per-sync counters a SyncAdapter fills in on
// the SyncResult it was handed. Some of them classify the sync as a hard or
// soft error (see SyncResult.hasHardError / hasSoftError), the rest are purely
// informational.
//
// Why Mac.apk needs it: SyncResult.stats is a public final field of this type,
// so any app that references SyncResult -- Telegram and Signal here -- also
// needs SyncStats to resolve.
//
// AOSP source mirrored: frameworks/base/core/java/android/content/
// SyncStats.java (android11-release), all nine counters plus clear/toString/
// Parcelable. This one is a plain mutable value type with no system
// dependency, so it is a complete implementation, not an approximation.
package android.content;

import android.os.Parcel;
import android.os.Parcelable;

public class SyncStats implements Parcelable {
    /** Counter for tracking how many authentication exceptions occurred. */
    public long numAuthExceptions;

    /** Counter for tracking how many IO exceptions occurred. */
    public long numIoExceptions;

    /** Counter for tracking how many parse exceptions occurred. */
    public long numParseExceptions;

    /** Counter for tracking how many conflicts were detected. */
    public long numConflictDetectedExceptions;

    /** Counter for tracking how many entries were inserted. */
    public long numInserts;

    /** Counter for tracking how many entries were updated. */
    public long numUpdates;

    /** Counter for tracking how many entries were deleted. */
    public long numDeletes;

    /** Counter for tracking how many entries were seen. */
    public long numEntries;

    /** Counter for tracking how many entries were skipped. */
    public long numSkippedEntries;

    public SyncStats() {
        numAuthExceptions = 0;
        numIoExceptions = 0;
        numParseExceptions = 0;
        numConflictDetectedExceptions = 0;
        numInserts = 0;
        numUpdates = 0;
        numDeletes = 0;
        numEntries = 0;
        numSkippedEntries = 0;
    }

    public SyncStats(Parcel in) {
        numAuthExceptions = in.readLong();
        numIoExceptions = in.readLong();
        numParseExceptions = in.readLong();
        numConflictDetectedExceptions = in.readLong();
        numInserts = in.readLong();
        numUpdates = in.readLong();
        numDeletes = in.readLong();
        numEntries = in.readLong();
        numSkippedEntries = in.readLong();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(" stats [");
        if (numAuthExceptions > 0) sb.append(" numAuthExceptions: ").append(numAuthExceptions);
        if (numIoExceptions > 0) sb.append(" numIoExceptions: ").append(numIoExceptions);
        if (numParseExceptions > 0) sb.append(" numParseExceptions: ").append(numParseExceptions);
        if (numConflictDetectedExceptions > 0)
            sb.append(" numConflictDetectedExceptions: ").append(numConflictDetectedExceptions);
        if (numInserts > 0) sb.append(" numInserts: ").append(numInserts);
        if (numUpdates > 0) sb.append(" numUpdates: ").append(numUpdates);
        if (numDeletes > 0) sb.append(" numDeletes: ").append(numDeletes);
        if (numEntries > 0) sb.append(" numEntries: ").append(numEntries);
        if (numSkippedEntries > 0) sb.append(" numSkippedEntries: ").append(numSkippedEntries);
        sb.append("]");
        return sb.toString();
    }

    /** Reset all the counters to 0. */
    public void clear() {
        numAuthExceptions = 0;
        numIoExceptions = 0;
        numParseExceptions = 0;
        numConflictDetectedExceptions = 0;
        numInserts = 0;
        numUpdates = 0;
        numDeletes = 0;
        numEntries = 0;
        numSkippedEntries = 0;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(numAuthExceptions);
        dest.writeLong(numIoExceptions);
        dest.writeLong(numParseExceptions);
        dest.writeLong(numConflictDetectedExceptions);
        dest.writeLong(numInserts);
        dest.writeLong(numUpdates);
        dest.writeLong(numDeletes);
        dest.writeLong(numEntries);
        dest.writeLong(numSkippedEntries);
    }

    public static final Creator<SyncStats> CREATOR = new Creator<SyncStats>() {
        public SyncStats createFromParcel(Parcel in) {
            return new SyncStats(in);
        }

        public SyncStats[] newArray(int size) {
            return new SyncStats[size];
        }
    };
}
