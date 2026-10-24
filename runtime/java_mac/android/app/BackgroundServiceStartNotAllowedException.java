// android.app.BackgroundServiceStartNotAllowedException -- thrown when an app in the background tries to start a service (API 31).
//   AOSP: frameworks/base/core/java/android/app/BackgroundServiceStartNotAllowedException.java
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

public final class BackgroundServiceStartNotAllowedException
        extends ServiceStartNotAllowedException implements Parcelable {
    public BackgroundServiceStartNotAllowedException(String message) {
        super(message);
    }

    BackgroundServiceStartNotAllowedException(Parcel source) {
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

    public static final Creator<BackgroundServiceStartNotAllowedException> CREATOR =
            new Creator<BackgroundServiceStartNotAllowedException>() {
                @Override
                public BackgroundServiceStartNotAllowedException createFromParcel(Parcel source) {
                    return new BackgroundServiceStartNotAllowedException(source);
                }

                @Override
                public BackgroundServiceStartNotAllowedException[] newArray(int size) {
                    return new BackgroundServiceStartNotAllowedException[size];
                }
            };
}
