// android.telecom.CallEndpointException -- the error Telecom reports through
// the OutcomeReceiver of Connection.requestCallEndpointChange() and
// CallControl.requestCallEndpointChange().
//
// A REAL implementation (plain RuntimeException + Parcelable value), and the
// value Mac.apk actually USES: with no Telecom service there is no endpoint to
// route call audio to, so requestCallEndpointChange reports
// ERROR_ENDPOINT_DOES_NOT_EXIST rather than silently succeeding and leaving
// the app waiting for an onCallEndpointChanged that cannot arrive.
//
// Mirrors telecomm/java/android/telecom/CallEndpointException.java; codes and
// public surface confirmed against frameworks/base/core/api/current.txt.
package android.telecom;

import android.os.Parcel;
import android.os.Parcelable;

public final class CallEndpointException extends RuntimeException implements Parcelable {

    /** Key under which Telecom bundles this exception. */
    public static final String CHANGE_ERROR = "ChangeErrorKey";

    public static final int ERROR_ENDPOINT_DOES_NOT_EXIST = 1;
    public static final int ERROR_REQUEST_TIME_OUT = 2;
    public static final int ERROR_ANOTHER_REQUEST = 3;
    public static final int ERROR_UNSPECIFIED = 4;

    private final int mCode;

    public CallEndpointException(String message, int code) {
        super(message);
        mCode = code;
    }

    public int getCode() {
        return mCode;
    }

    @Override public int describeContents() {
        return 0;
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getMessage());
        dest.writeInt(mCode);
    }

    public static final Creator<CallEndpointException> CREATOR =
            new Creator<CallEndpointException>() {
        @Override public CallEndpointException createFromParcel(Parcel source) {
            return new CallEndpointException(source.readString(), source.readInt());
        }

        @Override public CallEndpointException[] newArray(int size) {
            return new CallEndpointException[size];
        }
    };
}
