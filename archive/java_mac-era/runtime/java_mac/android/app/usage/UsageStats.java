// android.app.usage.UsageStats -- "Contains usage statistics for an app package
// for a specific time range." API 21.
//
// AOSP ref: frameworks/base/core/java/android/app/usage/UsageStats.java
// (android16-release). A plain accumulating value object: the system's
// UsageStatsService fills it in, and UsageStatsManager.queryUsageStats hands
// copies out.
//
// Mac.apk collects no usage history (see UsageStatsManager's class comment), so
// the manager returns an empty list and nothing here is ever populated by the
// runtime. The class exists because queryUsageStats's element type names it --
// an app iterating the list must be able to declare the loop variable -- and
// because the copy constructor and add() are how an app aggregates buckets it
// obtained itself.
package android.app.usage;

import android.os.Parcel;
import android.os.Parcelable;

public final class UsageStats implements Parcelable {

    String mPackageName;
    long mBeginTimeStamp;
    long mEndTimeStamp;
    long mLastTimeUsed;
    long mLastTimeVisible;
    long mLastTimeForegroundServiceUsed;
    long mTotalTimeInForeground;
    long mTotalTimeVisible;
    long mTotalTimeForegroundServiceUsed;

    public UsageStats() { }

    /** AOSP UsageStats(UsageStats): the copy constructor. */
    public UsageStats(UsageStats stats) {
        if (stats == null) return;
        mPackageName = stats.mPackageName;
        mBeginTimeStamp = stats.mBeginTimeStamp;
        mEndTimeStamp = stats.mEndTimeStamp;
        mLastTimeUsed = stats.mLastTimeUsed;
        mLastTimeVisible = stats.mLastTimeVisible;
        mLastTimeForegroundServiceUsed = stats.mLastTimeForegroundServiceUsed;
        mTotalTimeInForeground = stats.mTotalTimeInForeground;
        mTotalTimeVisible = stats.mTotalTimeVisible;
        mTotalTimeForegroundServiceUsed = stats.mTotalTimeForegroundServiceUsed;
    }

    private UsageStats(Parcel in) {
        mPackageName = in.readString();
        mBeginTimeStamp = in.readLong();
        mEndTimeStamp = in.readLong();
        mLastTimeUsed = in.readLong();
        mLastTimeVisible = in.readLong();
        mLastTimeForegroundServiceUsed = in.readLong();
        mTotalTimeInForeground = in.readLong();
        mTotalTimeVisible = in.readLong();
        mTotalTimeForegroundServiceUsed = in.readLong();
    }

    public String getPackageName() { return mPackageName; }
    /** AOSP getFirstTimeStamp(): "the beginning of the time range this
     *  UsageStats represents, measured in milliseconds since the epoch." */
    public long getFirstTimeStamp() { return mBeginTimeStamp; }
    public long getLastTimeStamp() { return mEndTimeStamp; }
    public long getLastTimeUsed() { return mLastTimeUsed; }
    public long getLastTimeVisible() { return mLastTimeVisible; }
    public long getLastTimeForegroundServiceUsed() { return mLastTimeForegroundServiceUsed; }
    public long getTotalTimeInForeground() { return mTotalTimeInForeground; }
    public long getTotalTimeVisible() { return mTotalTimeVisible; }
    public long getTotalTimeForegroundServiceUsed() { return mTotalTimeForegroundServiceUsed; }

    /** AOSP add(UsageStats right): "Add the statistics from the right
     *  UsageStats to the left." AOSP THROWS IllegalArgumentException when the
     *  package names differ -- that check is the observable half, and it is what
     *  stops an app silently summing two different apps' usage. The time range
     *  widens to cover both; the totals sum; each "last time" takes the later. */
    public void add(UsageStats right) {
        if (right == null) return;
        if (mPackageName != null && right.mPackageName != null
                && !mPackageName.equals(right.mPackageName)) {
            throw new IllegalArgumentException("Can't merge UsageStats for package '"
                    + mPackageName + "' with UsageStats for package '"
                    + right.mPackageName + "'.");
        }
        if (right.mBeginTimeStamp > mBeginTimeStamp) {
            // AOSP: the newer bucket's "last used" values win.
            mLastTimeUsed = Math.max(mLastTimeUsed, right.mLastTimeUsed);
            mLastTimeVisible = Math.max(mLastTimeVisible, right.mLastTimeVisible);
            mLastTimeForegroundServiceUsed = Math.max(mLastTimeForegroundServiceUsed,
                    right.mLastTimeForegroundServiceUsed);
        }
        mBeginTimeStamp = Math.min(mBeginTimeStamp, right.mBeginTimeStamp);
        mEndTimeStamp = Math.max(mEndTimeStamp, right.mEndTimeStamp);
        mTotalTimeInForeground += right.mTotalTimeInForeground;
        mTotalTimeVisible += right.mTotalTimeVisible;
        mTotalTimeForegroundServiceUsed += right.mTotalTimeForegroundServiceUsed;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPackageName);
        dest.writeLong(mBeginTimeStamp);
        dest.writeLong(mEndTimeStamp);
        dest.writeLong(mLastTimeUsed);
        dest.writeLong(mLastTimeVisible);
        dest.writeLong(mLastTimeForegroundServiceUsed);
        dest.writeLong(mTotalTimeInForeground);
        dest.writeLong(mTotalTimeVisible);
        dest.writeLong(mTotalTimeForegroundServiceUsed);
    }

    public static final Parcelable.Creator<UsageStats> CREATOR =
            new Parcelable.Creator<UsageStats>() {
        @Override public UsageStats createFromParcel(Parcel in) { return new UsageStats(in); }
        @Override public UsageStats[] newArray(int size) { return new UsageStats[size]; }
    };
}
