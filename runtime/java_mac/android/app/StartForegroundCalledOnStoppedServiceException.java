// android.app.StartForegroundCalledOnStoppedServiceException -- thrown when startForeground() is called on a service that was already stopped (API 34).
//   AOSP: frameworks/base/core/java/android/app/StartForegroundCalledOnStoppedServiceException.java
//         (android14-release), mirrored member for member: final, Parcelable,
//         and it parcels ONLY the message, exactly as AOSP does.
//
// Mac.apk never throws this (there is no background-start or foreground-type
// policy to enforce), but apps name it in catch clauses and in instanceof
// checks guarded by SDK_INT, and we present SDK 34, so the class must exist
// with the AOSP hierarchy. Census: docs/fwgap-new-platform-classes_2026-09-26.md.
package android.app;

import android.os.Parcel;
import android.os.Parcelable;

public final class StartForegroundCalledOnStoppedServiceException
        extends IllegalStateException implements Parcelable {
    public StartForegroundCalledOnStoppedServiceException(String message) {
        super(message);
    }

    StartForegroundCalledOnStoppedServiceException(Parcel source) {
        super(source.readString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getMessage());
    }

    public static final Creator<StartForegroundCalledOnStoppedServiceException> CREATOR =
            new Creator<StartForegroundCalledOnStoppedServiceException>() {
                @Override
                public StartForegroundCalledOnStoppedServiceException createFromParcel(Parcel source) {
                    return new StartForegroundCalledOnStoppedServiceException(source);
                }

                @Override
                public StartForegroundCalledOnStoppedServiceException[] newArray(int size) {
                    return new StartForegroundCalledOnStoppedServiceException[size];
                }
            };
}
