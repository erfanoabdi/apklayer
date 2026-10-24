// android.telecom.CallEndpoint -- one audio destination a call can be routed
// to (earpiece, speaker, a Bluetooth headset, ...).
//
// A REAL value object: constructor, getters, equality and parcelling are pure
// data with no device dependency, and an app may legitimately build one to ask
// for a route change. What never happens here is Telecom HANDING one back --
// Connection.getCurrentCallEndpoint() stays null because there is no Telecom
// service enumerating the host's audio devices for a call.
//
// Mirrors telecomm/java/android/telecom/CallEndpoint.java (API 34); constants
// and public surface confirmed against frameworks/base/core/api/current.txt.
package android.telecom;

import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;

import java.util.Objects;
import java.util.UUID;

public final class CallEndpoint implements Parcelable {

    /** @hide on AOSP; the result codes Telecom reports for a route change. */
    public static final int ENDPOINT_OPERATION_SUCCESS = 0;
    public static final int ENDPOINT_OPERATION_FAILED = 1;

    public static final int TYPE_UNKNOWN = -1;
    public static final int TYPE_EARPIECE = 1;
    public static final int TYPE_BLUETOOTH = 2;
    public static final int TYPE_WIRED_HEADSET = 3;
    public static final int TYPE_SPEAKER = 4;
    public static final int TYPE_STREAMING = 5;

    private final CharSequence mName;
    private final int mType;
    private final ParcelUuid mIdentifier;

    public CallEndpoint(CharSequence name, int type, ParcelUuid id) {
        mName = name;
        mType = type;
        mIdentifier = id;
    }

    /**
     * @hide on AOSP. Generates the identifier rather than taking one, which is
     * why the public 3-arg form above is the one apps use.
     */
    public CallEndpoint(CharSequence name, int type) {
        this(name, type, new ParcelUuid(UUID.randomUUID()));
    }

    /** @hide on AOSP: copy constructor. */
    public CallEndpoint(CallEndpoint endpoint) {
        mName = endpoint.getEndpointName();
        mType = endpoint.getEndpointType();
        mIdentifier = endpoint.getIdentifier();
    }

    public CharSequence getEndpointName() {
        return mName;
    }

    public int getEndpointType() {
        return mType;
    }

    public ParcelUuid getIdentifier() {
        return mIdentifier;
    }

    public static String endpointTypeToString(int endpointType) {
        switch (endpointType) {
            case TYPE_EARPIECE: return "EARPIECE";
            case TYPE_BLUETOOTH: return "BLUETOOTH";
            case TYPE_WIRED_HEADSET: return "WIRED_HEADSET";
            case TYPE_SPEAKER: return "SPEAKER";
            case TYPE_STREAMING: return "EXTERNAL";
            default: return "UNKNOWN (" + endpointType + ")";
        }
    }

    @Override public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof CallEndpoint)) return false;
        CallEndpoint endpoint = (CallEndpoint) obj;
        return Objects.equals(mName, endpoint.getEndpointName())
                && mType == endpoint.getEndpointType()
                && Objects.equals(mIdentifier, endpoint.getIdentifier());
    }

    @Override public int hashCode() {
        return Objects.hash(mName, mType, mIdentifier);
    }

    @Override public String toString() {
        return "CallEndpoint(" + endpointTypeToString(mType) + ", " + mName + ")";
    }

    @Override public int describeContents() {
        return 0;
    }

    @Override public void writeToParcel(Parcel destination, int flags) {
        destination.writeCharSequence(mName);
        destination.writeInt(mType);
        destination.writeParcelable(mIdentifier, flags);
    }

    public static final Creator<CallEndpoint> CREATOR = new Creator<CallEndpoint>() {
        @Override public CallEndpoint createFromParcel(Parcel source) {
            CharSequence name = source.readCharSequence();
            int type = source.readInt();
            ParcelUuid id = source.readParcelable(ParcelUuid.class.getClassLoader());
            return new CallEndpoint(name, type, id);
        }

        @Override public CallEndpoint[] newArray(int size) {
            return new CallEndpoint[size];
        }
    };
}
