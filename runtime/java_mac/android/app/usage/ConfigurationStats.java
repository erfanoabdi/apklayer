// android.app.usage.ConfigurationStats -- "Represents the usage statistics of a
// device Configuration for a specific time range." API 21. Returned by
// UsageStatsManager.queryConfigurations.
//
// AOSP ref: frameworks/base/core/java/android/app/usage/ConfigurationStats.java
// (android16-release). See UsageStats for why nothing here is populated by the
// runtime.
package android.app.usage;

import android.content.res.Configuration;
import android.os.Parcel;
import android.os.Parcelable;

public final class ConfigurationStats implements Parcelable {

    Configuration mConfiguration;
    long mBeginTimeStamp;
    long mEndTimeStamp;
    long mLastTimeActive;
    long mTotalTimeActive;
    int mActivationCount;

    public ConfigurationStats() { }

    /** AOSP ConfigurationStats(ConfigurationStats): the copy constructor. AOSP
     *  shares the Configuration reference rather than copying it. */
    public ConfigurationStats(ConfigurationStats stats) {
        if (stats == null) return;
        mConfiguration = stats.mConfiguration;
        mBeginTimeStamp = stats.mBeginTimeStamp;
        mEndTimeStamp = stats.mEndTimeStamp;
        mLastTimeActive = stats.mLastTimeActive;
        mTotalTimeActive = stats.mTotalTimeActive;
        mActivationCount = stats.mActivationCount;
    }

    private ConfigurationStats(Parcel in) {
        mConfiguration = in.readTypedObject(Configuration.CREATOR);
        mBeginTimeStamp = in.readLong();
        mEndTimeStamp = in.readLong();
        mLastTimeActive = in.readLong();
        mTotalTimeActive = in.readLong();
        mActivationCount = in.readInt();
    }

    public Configuration getConfiguration() { return mConfiguration; }
    public long getFirstTimeStamp() { return mBeginTimeStamp; }
    public long getLastTimeStamp() { return mEndTimeStamp; }
    /** AOSP getLastTimeActive(): "the last time this configuration was active,
     *  measured in milliseconds since the epoch." */
    public long getLastTimeActive() { return mLastTimeActive; }
    /** AOSP getTotalTimeActive(): "the total time this configuration was
     *  active, measured in milliseconds." */
    public long getTotalTimeActive() { return mTotalTimeActive; }
    /** AOSP getActivationCount(): "the number of times this configuration was
     *  active." */
    public int getActivationCount() { return mActivationCount; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mConfiguration, flags);
        dest.writeLong(mBeginTimeStamp);
        dest.writeLong(mEndTimeStamp);
        dest.writeLong(mLastTimeActive);
        dest.writeLong(mTotalTimeActive);
        dest.writeInt(mActivationCount);
    }

    public static final Parcelable.Creator<ConfigurationStats> CREATOR =
            new Parcelable.Creator<ConfigurationStats>() {
        @Override public ConfigurationStats createFromParcel(Parcel in) {
            return new ConfigurationStats(in);
        }
        @Override public ConfigurationStats[] newArray(int size) {
            return new ConfigurationStats[size];
        }
    };
}
