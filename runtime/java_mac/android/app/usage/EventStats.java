// android.app.usage.EventStats -- "Contains usage statistics for an event type
// for a specific time range." API 28. Returned by
// UsageStatsManager.queryEventStats.
//
// AOSP ref: frameworks/base/core/java/android/app/usage/EventStats.java
// (android16-release). Same shape as UsageStats but keyed by event TYPE rather
// than by package; see UsageStats for why nothing here is ever populated by the
// runtime.
package android.app.usage;

import android.os.Parcel;
import android.os.Parcelable;

public final class EventStats implements Parcelable {

    int mEventType;
    long mBeginTimeStamp;
    long mEndTimeStamp;
    long mLastEventTime;
    long mTotalTime;
    int mCount;

    public EventStats() { }

    /** AOSP EventStats(EventStats): the copy constructor. */
    public EventStats(EventStats stats) {
        if (stats == null) return;
        mEventType = stats.mEventType;
        mBeginTimeStamp = stats.mBeginTimeStamp;
        mEndTimeStamp = stats.mEndTimeStamp;
        mLastEventTime = stats.mLastEventTime;
        mTotalTime = stats.mTotalTime;
        mCount = stats.mCount;
    }

    private EventStats(Parcel in) {
        mEventType = in.readInt();
        mBeginTimeStamp = in.readLong();
        mEndTimeStamp = in.readLong();
        mLastEventTime = in.readLong();
        mTotalTime = in.readLong();
        mCount = in.readInt();
    }

    /** AOSP getEventType(): one of the UsageEvents.Event types this bucket
     *  counts. */
    public int getEventType() { return mEventType; }
    public long getFirstTimeStamp() { return mBeginTimeStamp; }
    public long getLastTimeStamp() { return mEndTimeStamp; }
    /** AOSP getLastEventTime(): "the last time this event occurred", in
     *  milliseconds since the epoch. */
    public long getLastEventTime() { return mLastEventTime; }
    /** AOSP getTotalTime(): "the total time this event was active, measured in
     *  milliseconds." */
    public long getTotalTime() { return mTotalTime; }
    /** AOSP getCount(): "the number of times this event occurred." */
    public int getCount() { return mCount; }

    /** AOSP add(EventStats right): merges two buckets. AOSP throws
     *  IllegalArgumentException when the event types differ, for the same
     *  reason UsageStats.add rejects a package mismatch. */
    public void add(EventStats right) {
        if (right == null) return;
        if (mEventType != right.mEventType) {
            throw new IllegalArgumentException("Can't merge EventStats for event #"
                    + mEventType + " with EventStats for event #" + right.mEventType + ".");
        }
        if (right.mBeginTimeStamp > mBeginTimeStamp) {
            mLastEventTime = Math.max(mLastEventTime, right.mLastEventTime);
        }
        mBeginTimeStamp = Math.min(mBeginTimeStamp, right.mBeginTimeStamp);
        mEndTimeStamp = Math.max(mEndTimeStamp, right.mEndTimeStamp);
        mTotalTime += right.mTotalTime;
        mCount += right.mCount;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mEventType);
        dest.writeLong(mBeginTimeStamp);
        dest.writeLong(mEndTimeStamp);
        dest.writeLong(mLastEventTime);
        dest.writeLong(mTotalTime);
        dest.writeInt(mCount);
    }

    public static final Parcelable.Creator<EventStats> CREATOR =
            new Parcelable.Creator<EventStats>() {
        @Override public EventStats createFromParcel(Parcel in) { return new EventStats(in); }
        @Override public EventStats[] newArray(int size) { return new EventStats[size]; }
    };
}
