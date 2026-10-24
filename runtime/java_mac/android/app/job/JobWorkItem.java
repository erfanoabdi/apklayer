// android.app.job.JobWorkItem -- "A unit of work that can be enqueued for a job
// using JobScheduler.enqueue." AOSP API-26+.
//
// AOSP ref: apex/jobscheduler/framework/java/android/app/job/JobWorkItem.java
// (android16-release). It is a value carrier: the Intent describing the work,
// the app's own network-bytes estimate, an extras Bundle, and a delivery count
// the scheduler increments each time the item is handed out.
//
// androidx.core.app.JobIntentService is built on the enqueue/dequeueWork pair,
// so this type is on a live path -- see JobParameters.dequeueWork.
package android.app.job;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public final class JobWorkItem implements Parcelable {
    private final Intent intent;
    private final long estimatedDownloadBytes;
    private final long estimatedUploadBytes;
    private final long minimumChunkBytes;
    private final Bundle extras;
    /** AOSP's JobServiceContext calls bumpDeliveryCount() each time the item is
     *  handed to the job, starting from 0 -- so the FIRST delivery reports 1.
     *  Package-private so MacApkJobScheduler owns the bookkeeping, exactly as
     *  the system does; getDeliveryCount() used to be a constant 1, which meant
     *  a JobIntentService retrying an item could not tell a redelivery from a
     *  first attempt. */
    int deliveryCount;

    public JobWorkItem(Intent intent) {
        this(intent, JobInfo.NETWORK_BYTES_UNKNOWN, JobInfo.NETWORK_BYTES_UNKNOWN);
    }

    /** AOSP JobWorkItem(Intent, long downloadBytes, long uploadBytes): the
     *  app's transfer estimate for THIS item. */
    public JobWorkItem(Intent intent, long downloadBytes, long uploadBytes) {
        this(intent, downloadBytes, uploadBytes, JobInfo.NETWORK_BYTES_UNKNOWN);
    }

    /** AOSP JobWorkItem(Intent, long downloadBytes, long uploadBytes, long
     *  minimumChunkBytes): adds "the smallest piece of data that cannot be
     *  easily paused and resumed". AOSP validates the three byte counts the
     *  same way JobInfo.Builder does. */
    public JobWorkItem(Intent intent, long downloadBytes, long uploadBytes,
            long minimumChunkBytes) {
        if (downloadBytes < 0 && downloadBytes != JobInfo.NETWORK_BYTES_UNKNOWN) {
            throw new IllegalArgumentException("Invalid download bytes");
        }
        if (uploadBytes < 0 && uploadBytes != JobInfo.NETWORK_BYTES_UNKNOWN) {
            throw new IllegalArgumentException("Invalid upload bytes");
        }
        if (minimumChunkBytes != JobInfo.NETWORK_BYTES_UNKNOWN && minimumChunkBytes <= 0) {
            throw new IllegalArgumentException("Minimum chunk size must be positive");
        }
        this.intent = intent;
        this.estimatedDownloadBytes = downloadBytes;
        this.estimatedUploadBytes = uploadBytes;
        this.minimumChunkBytes = minimumChunkBytes;
        this.extras = new Bundle();
    }

    private JobWorkItem(Parcel in) {
        this.intent = in.readInt() != 0 ? Intent.CREATOR.createFromParcel(in) : null;
        this.estimatedDownloadBytes = in.readLong();
        this.estimatedUploadBytes = in.readLong();
        this.minimumChunkBytes = in.readLong();
        Bundle b = in.readBundle();
        this.extras = (b != null) ? b : new Bundle();
        this.deliveryCount = in.readInt();
    }

    public Intent getIntent() { return intent; }

    /** AOSP getDeliveryCount(): "the number of times this work has been
     *  delivered to the job", starting at 1. */
    public int getDeliveryCount() { return deliveryCount; }

    /** AOSP getEstimatedNetworkDownloadBytes() / getEstimatedNetworkUploadBytes()
     *  / getMinimumNetworkChunkBytes(): the values handed to the constructor, or
     *  JobInfo.NETWORK_BYTES_UNKNOWN. */
    public long getEstimatedNetworkDownloadBytes() { return estimatedDownloadBytes; }
    public long getEstimatedNetworkUploadBytes() { return estimatedUploadBytes; }
    public long getMinimumNetworkChunkBytes() { return minimumChunkBytes; }

    /** AOSP getExtras(): @NonNull -- "the extras associated with this work",
     *  the Bundle an app attaches to a single item. */
    public Bundle getExtras() { return extras; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        if (intent != null) {
            dest.writeInt(1);
            intent.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        dest.writeLong(estimatedDownloadBytes);
        dest.writeLong(estimatedUploadBytes);
        dest.writeLong(minimumChunkBytes);
        dest.writeBundle(extras);
        dest.writeInt(deliveryCount);
    }

    @Override public String toString() {
        return "JobWorkItem{intent=" + intent + " dcount=" + deliveryCount + "}";
    }

    public static final Parcelable.Creator<JobWorkItem> CREATOR =
            new Parcelable.Creator<JobWorkItem>() {
        @Override public JobWorkItem createFromParcel(Parcel in) { return new JobWorkItem(in); }
        @Override public JobWorkItem[] newArray(int size) { return new JobWorkItem[size]; }
    };
}
