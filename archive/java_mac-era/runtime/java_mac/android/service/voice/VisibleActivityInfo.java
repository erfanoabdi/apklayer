package android.service.voice;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * API 33 notification to the assistant that an activity became visible, so it
 * can ask that activity for its direct actions.
 *
 * See the package note on VoiceInteractionService.
 *
 * Source: frameworks/base/core/java/android/service/voice/VisibleActivityInfo.java
 */
public final class VisibleActivityInfo implements Parcelable {

    private final VoiceInteractionSession.ActivityId mActivityId;

    // AOSP's constructor is @hide; the platform builds these.
    VisibleActivityInfo(VoiceInteractionSession.ActivityId activityId) {
        mActivityId = activityId;
    }

    public VoiceInteractionSession.ActivityId getActivityId() { return mActivityId; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        // ActivityId is an opaque token in AOSP (its public API has no members
        // at all), so there is nothing of it to write.
    }

    public static final Creator<VisibleActivityInfo> CREATOR =
            new Creator<VisibleActivityInfo>() {
        @Override public VisibleActivityInfo createFromParcel(Parcel source) {
            return new VisibleActivityInfo(new VoiceInteractionSession.ActivityId());
        }
        @Override public VisibleActivityInfo[] newArray(int size) {
            return new VisibleActivityInfo[size];
        }
    };
}
