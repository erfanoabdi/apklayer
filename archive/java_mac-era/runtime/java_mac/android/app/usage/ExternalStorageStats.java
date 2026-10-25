// android.app.usage.ExternalStorageStats -- "Shared/external storage statistics
// for a UUID and user", returned by
// StorageStatsManager.queryExternalStatsForUser. API 26.
//
// AOSP ref: frameworks/base/core/java/android/app/usage/ExternalStorageStats.java
// (android16-release). A plain value carrier of five byte totals.
package android.app.usage;

import android.os.Parcel;
import android.os.Parcelable;

public final class ExternalStorageStats implements Parcelable {

    long totalBytes;
    long audioBytes;
    long videoBytes;
    long imageBytes;
    long appBytes;

    /** @hide -- AOSP's constructor is @hide too; only the system produces
     *  these. */
    ExternalStorageStats() { }

    private ExternalStorageStats(Parcel in) {
        totalBytes = in.readLong();
        audioBytes = in.readLong();
        videoBytes = in.readLong();
        imageBytes = in.readLong();
        appBytes = in.readLong();
    }

    /** AOSP getTotalBytes(): "the total bytes used by all files in the shared
     *  storage hosted on this volume." */
    public long getTotalBytes() { return totalBytes; }
    /** AOSP getAudioBytes(): the subset that is audio. */
    public long getAudioBytes() { return audioBytes; }
    /** AOSP getVideoBytes(): the subset that is video. */
    public long getVideoBytes() { return videoBytes; }
    /** AOSP getImageBytes(): the subset that is images. */
    public long getImageBytes() { return imageBytes; }
    /** AOSP getAppBytes(): the subset that is app files (Android/data,
     *  Android/obb). */
    public long getAppBytes() { return appBytes; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(totalBytes);
        dest.writeLong(audioBytes);
        dest.writeLong(videoBytes);
        dest.writeLong(imageBytes);
        dest.writeLong(appBytes);
    }

    public static final Parcelable.Creator<ExternalStorageStats> CREATOR =
            new Parcelable.Creator<ExternalStorageStats>() {
        @Override public ExternalStorageStats createFromParcel(Parcel in) {
            return new ExternalStorageStats(in);
        }
        @Override public ExternalStorageStats[] newArray(int size) {
            return new ExternalStorageStats[size];
        }
    };
}
