// android.telecom.CallException -- the error type Telecom reports through the
// OutcomeReceiver of a transactional call operation (TelecomManager.addCall
// and every CallControl method).
//
// A REAL implementation, and a LOAD-BEARING one here: this is the value
// Mac.apk hands back to say "this device has no Telecom service", so an app
// takes its own fallback path instead of waiting forever for a callback that
// cannot arrive. See CallControl and TelecomManager for the reasoning.
//
// Mirrors telecomm/java/android/telecom/CallException.java (API 34); code
// values and public surface confirmed against
// frameworks/base/core/api/current.txt.
package android.telecom;

import android.os.Parcel;
import android.os.Parcelable;

public final class CallException extends RuntimeException implements Parcelable {

    /** Key under which Telecom bundles this exception. */
    public static final String TRANSACTION_EXCEPTION_KEY = "TelecomTransactionalExceptionKey";

    public static final int CODE_ERROR_UNKNOWN = 1;
    public static final int CODE_CANNOT_HOLD_CURRENT_ACTIVE_CALL = 2;
    public static final int CODE_CALL_IS_NOT_BEING_TRACKED = 3;
    public static final int CODE_CALL_CANNOT_BE_SET_TO_ACTIVE = 4;
    public static final int CODE_CALL_NOT_PERMITTED_AT_PRESENT_TIME = 5;
    public static final int CODE_OPERATION_TIMED_OUT = 6;

    private final int mCode;

    public CallException(String message, int code) {
        super(getMessage(message, code));
        mCode = code;
    }

    /** @hide on AOSP: message-only form defaults the code to unknown. */
    public CallException(String message) {
        this(message, CODE_ERROR_UNKNOWN);
    }

    public int getCode() {
        return mCode;
    }

    private static String getMessage(String message, int code) {
        StringBuilder builder = new StringBuilder();
        if (message != null) {
            builder.append(message);
            builder.append(" ");
        }
        builder.append("[CallException code=").append(code).append("]");
        return builder.toString();
    }

    @Override public int describeContents() {
        return 0;
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getMessage());
        dest.writeInt(mCode);
    }

    public static final Creator<CallException> CREATOR = new Creator<CallException>() {
        @Override public CallException createFromParcel(Parcel source) {
            return new CallException(source.readString(), source.readInt());
        }

        @Override public CallException[] newArray(int size) {
            return new CallException[size];
        }
    };
}
