// android.app.InvalidForegroundServiceTypeException -- thrown when a foreground service asks for a type its manifest does not declare (API 34).
//   AOSP: frameworks/base/core/java/android/app/InvalidForegroundServiceTypeException.java
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

public final class InvalidForegroundServiceTypeException
        extends ForegroundServiceTypeException implements Parcelable {
    public InvalidForegroundServiceTypeException(String message) {
        super(message);
    }

    InvalidForegroundServiceTypeException(Parcel source) {
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

    public static final Creator<InvalidForegroundServiceTypeException> CREATOR =
            new Creator<InvalidForegroundServiceTypeException>() {
                @Override
                public InvalidForegroundServiceTypeException createFromParcel(Parcel source) {
                    return new InvalidForegroundServiceTypeException(source);
                }

                @Override
                public InvalidForegroundServiceTypeException[] newArray(int size) {
                    return new InvalidForegroundServiceTypeException[size];
                }
            };
}
