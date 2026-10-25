// android.telecom.QueryLocationException -- the error Telecom reports through
// the OutcomeReceiver of Connection.queryLocationForEmergency().
//
// A REAL implementation (plain RuntimeException + Parcelable value), and the
// value Mac.apk actually USES: queryLocationForEmergency asks the CELLULAR
// network for the device's location during an emergency call. A Mac has no
// radio and no emergency call, so the request reports
// ERROR_NOT_ALLOWED_FOR_NON_EMERGENCY_CONNECTIONS -- which is literally true
// here, since no Connection on this device is an emergency connection.
//
// Mirrors telecomm/java/android/telecom/QueryLocationException.java; codes and
// public surface confirmed against frameworks/base/core/api/current.txt.
package android.telecom;

import android.os.Parcel;
import android.os.Parcelable;

public final class QueryLocationException extends RuntimeException implements Parcelable {

    /** Key under which Telecom bundles this exception. */
    public static final String QUERY_LOCATION_ERROR = "QueryLocationErrorKey";

    public static final int ERROR_REQUEST_TIME_OUT = 1;
    public static final int ERROR_PREVIOUS_REQUEST_EXISTS = 2;
    public static final int ERROR_NOT_PERMITTED = 3;
    public static final int ERROR_NOT_ALLOWED_FOR_NON_EMERGENCY_CONNECTIONS = 4;
    public static final int ERROR_SERVICE_UNAVAILABLE = 5;
    public static final int ERROR_UNSPECIFIED = 6;

    private final int mCode;

    public QueryLocationException(String message) {
        this(message, ERROR_UNSPECIFIED);
    }

    public QueryLocationException(String message, int code) {
        super(message);
        mCode = code;
    }

    public QueryLocationException(String message, int code, Throwable cause) {
        super(message, cause);
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

    public static final Creator<QueryLocationException> CREATOR =
            new Creator<QueryLocationException>() {
        @Override public QueryLocationException createFromParcel(Parcel source) {
            return new QueryLocationException(source.readString(), source.readInt());
        }

        @Override public QueryLocationException[] newArray(int size) {
            return new QueryLocationException[size];
        }
    };
}
