// android.app.AuthenticationRequiredException -- a SecurityException that
// carries the PendingIntent a user must launch to authenticate (API 26).
// Thrown by a ContentProvider/DocumentsProvider when content exists but the
// user has to sign in first; ContentResolver callers catch it and launch
// getUserAction().
//   AOSP: frameworks/base/core/java/android/app/AuthenticationRequiredException.java
//         (android14-release), mirrored member for member. The Parcel
//         constructor is @hide public in AOSP; it is package-private here so our
//         public surface matches the SDK (the CREATOR is the public way in).
// Census: docs/fwgap-new-platform-classes_2026-09-26.md.
package android.app;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

public final class AuthenticationRequiredException extends SecurityException implements Parcelable {
    private static final String TAG = "AuthenticationRequiredException";

    private final PendingIntent mUserAction;

    AuthenticationRequiredException(Parcel in) {
        this(new SecurityException(in.readString()), PendingIntent.CREATOR.createFromParcel(in));
    }

    /**
     * AOSP keeps only the cause's MESSAGE (super(cause.getMessage())), not the
     * cause itself, and rejects a null user action with NullPointerException.
     */
    public AuthenticationRequiredException(Throwable cause, PendingIntent userAction) {
        super(cause.getMessage());
        mUserAction = Objects.requireNonNull(userAction);
    }

    /** Returns an action that can be used to request that the user authenticate. */
    public PendingIntent getUserAction() {
        return mUserAction;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getMessage());
        mUserAction.writeToParcel(dest, flags);
    }

    public static final Creator<AuthenticationRequiredException> CREATOR =
            new Creator<AuthenticationRequiredException>() {
        @Override
        public AuthenticationRequiredException createFromParcel(Parcel source) {
            return new AuthenticationRequiredException(source);
        }

        @Override
        public AuthenticationRequiredException[] newArray(int size) {
            return new AuthenticationRequiredException[size];
        }
    };
}
