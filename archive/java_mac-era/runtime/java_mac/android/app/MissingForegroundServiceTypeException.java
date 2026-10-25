// android.app.MissingForegroundServiceTypeException -- thrown when an app targeting API 34 starts a foreground service with no type (API 34).
//   AOSP: frameworks/base/core/java/android/app/MissingForegroundServiceTypeException.java
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

public final class MissingForegroundServiceTypeException
        extends ForegroundServiceTypeException implements Parcelable {
    public MissingForegroundServiceTypeException(String message) {
        super(message);
    }

    MissingForegroundServiceTypeException(Parcel source) {
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

    public static final Creator<MissingForegroundServiceTypeException> CREATOR =
            new Creator<MissingForegroundServiceTypeException>() {
                @Override
                public MissingForegroundServiceTypeException createFromParcel(Parcel source) {
                    return new MissingForegroundServiceTypeException(source);
                }

                @Override
                public MissingForegroundServiceTypeException[] newArray(int size) {
                    return new MissingForegroundServiceTypeException[size];
                }
            };
}
