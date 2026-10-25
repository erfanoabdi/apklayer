package com.google.android.gms.common.api;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.IntentSender;
import android.os.Parcel;
import android.os.Parcelable;

// com.google.android.gms.common.api.Status -- the canonical GMS status object,
// carried by every Result and by a failed Task's ApiException. Implements Result
// (getStatus returns itself) and Parcelable, matching the play-services public
// type. The status constants reference CommonStatusCodes.
//
// Resolution: a Status may carry a PendingIntent the app can launch to resolve
// the condition (e.g. RESOLUTION_REQUIRED). On Mac.apk there is no Play Services
// system app, so we never manufacture a resolution PendingIntent; hasResolution
// is false and startResolutionForResult is a no-op (there is honestly nothing to
// resolve), exactly like a de-Googled device.
public final class Status implements Result, Parcelable {

    public static final Status SUCCESS = new Status(CommonStatusCodes.SUCCESS);
    public static final Status SUCCESS_CACHE = new Status(CommonStatusCodes.SUCCESS_CACHE);
    public static final Status INTERNAL_ERROR = new Status(CommonStatusCodes.INTERNAL_ERROR);
    public static final Status CANCELED = new Status(CommonStatusCodes.CANCELED);
    public static final Status RESULT_TIMEOUT = new Status(CommonStatusCodes.TIMEOUT);
    public static final Status RESULT_INTERRUPTED = new Status(CommonStatusCodes.INTERRUPTED);
    public static final Status RESULT_DEAD_CLIENT = new Status(CommonStatusCodes.DEAD_CLIENT);

    private final int statusCode;
    private final String statusMessage;
    private final PendingIntent resolution;

    public Status(int statusCode) { this(statusCode, null, null); }
    public Status(int statusCode, String statusMessage) { this(statusCode, statusMessage, null); }
    public Status(int statusCode, String statusMessage, PendingIntent resolution) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.resolution = resolution;
    }

    public int getStatusCode()       { return statusCode; }
    public String getStatusMessage() { return statusMessage; }
    public PendingIntent getResolution() { return resolution; }
    public boolean hasResolution()   { return resolution != null; }
    public boolean isSuccess()       { return statusCode <= 0; }
    public boolean isCanceled()      { return statusCode == CommonStatusCodes.CANCELED; }
    public boolean isInterrupted()   { return statusCode == CommonStatusCodes.INTERRUPTED; }

    @Override public Status getStatus() { return this; }

    /** Launches the resolution PendingIntent if one exists. On Mac.apk a Status
     *  never carries a resolution (no Play Services system app to resolve
     *  against), so this is a clean no-op exactly like a de-Googled device.
     *  The checked-exception declaration matches the GMS signature so callers
     *  that wrap it in try/catch still compile. */
    public void startResolutionForResult(Activity activity, int requestCode)
            throws IntentSender.SendIntentException {
        // resolution is always null here; nothing to launch.
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(statusCode);
        dest.writeString(statusMessage);
    }

    @Override public String toString() {
        return "Status{statusCode=" + CommonStatusCodes.getStatusCodeString(statusCode)
                + ", resolution=" + resolution + "}";
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Status)) return false;
        Status s = (Status) o;
        return statusCode == s.statusCode
                && (statusMessage == null ? s.statusMessage == null
                                          : statusMessage.equals(s.statusMessage))
                && (resolution == null ? s.resolution == null
                                       : resolution.equals(s.resolution));
    }
    @Override public int hashCode() {
        int h = statusCode;
        h = 31 * h + (statusMessage == null ? 0 : statusMessage.hashCode());
        h = 31 * h + (resolution == null ? 0 : resolution.hashCode());
        return h;
    }

    public static final Parcelable.Creator<Status> CREATOR =
            new Parcelable.Creator<Status>() {
        @Override public Status createFromParcel(Parcel in) {
            return new Status(in.readInt(), in.readString());
        }
        @Override public Status[] newArray(int size) { return new Status[size]; }
    };
}
