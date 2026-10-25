// android.os.health.TimerStat -- a (count, time) pair from a HealthStats timer.
//
// AOSP ref: frameworks/base/core/java/android/os/health/TimerStat.java
// (android16-release). Ported verbatim. Two things the previous stub got
// wrong, both observable: the class is Parcelable upstream (so a missing
// CREATOR made it fail a Bundle/Intent type check rather than throw a
// diagnosable NoSuchMethodError), and setCount/setTime are real MUTATORS --
// they were no-ops here, so a caller that built a TimerStat with the no-arg
// constructor and then set its fields read back zeros.
package android.os.health;

import android.os.Parcel;
import android.os.Parcelable;

public final class TimerStat implements Parcelable {
    private int mCount;
    private long mTime;

    public static final Parcelable.Creator<TimerStat> CREATOR =
            new Parcelable.Creator<TimerStat>() {
                public TimerStat createFromParcel(Parcel in) { return new TimerStat(in); }
                public TimerStat[] newArray(int size) { return new TimerStat[size]; }
            };

    public TimerStat() {
    }

    public TimerStat(int count, long time) {
        mCount = count;
        mTime = time;
    }

    public TimerStat(Parcel in) {
        mCount = in.readInt();
        mTime = in.readLong();
    }

    public int describeContents() { return 0; }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mCount);
        out.writeLong(mTime);
    }

    public void setCount(int count) { mCount = count; }
    public int getCount() { return mCount; }
    public void setTime(long time) { mTime = time; }
    public long getTime() { return mTime; }
}
