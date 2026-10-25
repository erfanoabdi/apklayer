// android.app.job.PendingJobReasonsInfo -- "A wrapper class for the reasons a
// job is pending, and the timestamp at which those reasons were recorded."
// Returned in a list by JobScheduler.getPendingJobReasonsHistory (API 36).
//
// AOSP ref: apex/jobscheduler/framework/java/android/app/job/
// PendingJobReasonsInfo.java (android16-release). A plain value carrier: a
// timestamp plus the JobScheduler.PENDING_JOB_REASON_* array that applied then.
package android.app.job;

import android.os.Parcel;
import android.os.Parcelable;

public final class PendingJobReasonsInfo implements Parcelable {

    private final long mTimestampMillis;
    private final int[] mPendingJobReasons;

    /** @hide -- AOSP's constructor is @hide too: the JobScheduler service is
     *  the only producer of these. */
    public PendingJobReasonsInfo(long timestampMillis, int[] reasons) {
        mTimestampMillis = timestampMillis;
        mPendingJobReasons = (reasons != null) ? reasons.clone() : new int[0];
    }

    private PendingJobReasonsInfo(Parcel in) {
        mTimestampMillis = in.readLong();
        int[] reasons = in.createIntArray();
        mPendingJobReasons = (reasons != null) ? reasons : new int[0];
    }

    /** AOSP getPendingJobReasons(): "the reasons why the job is pending" at
     *  this instant, as JobScheduler.PENDING_JOB_REASON_* values. Never null. */
    public int[] getPendingJobReasons() { return mPendingJobReasons.clone(); }

    /** AOSP getTimestampMillis(): "the timestamp at which these reasons were
     *  recorded", as elapsed-realtime milliseconds. */
    public long getTimestampMillis() { return mTimestampMillis; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mTimestampMillis);
        dest.writeIntArray(mPendingJobReasons);
    }

    public static final Parcelable.Creator<PendingJobReasonsInfo> CREATOR =
            new Parcelable.Creator<PendingJobReasonsInfo>() {
        @Override public PendingJobReasonsInfo createFromParcel(Parcel in) {
            return new PendingJobReasonsInfo(in);
        }
        @Override public PendingJobReasonsInfo[] newArray(int size) {
            return new PendingJobReasonsInfo[size];
        }
    };
}
