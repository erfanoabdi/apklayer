// android.telecom.CallAttributes -- what an app tells Telecom about a call it
// wants to add via TelecomManager.addCall (direction, audio vs video, display
// name, address, capabilities).
//
// A REAL value object with no device dependency: the app builds it, and the
// getters return what was built. Nothing here implies Telecom accepted it --
// that verdict comes from addCall, which reports failure honestly (see
// TelecomManager.addCall).
//
// The Builder's argument VALIDATION is copied deliberately rather than dropped:
// AOSP throws IllegalArgumentException for an out-of-range direction, and an
// app relying on that to reject bad input must still get it.
//
// Mirrors telecomm/java/android/telecom/CallAttributes.java (API 34); constants
// and public surface confirmed against frameworks/base/core/api/current.txt.
//
// NOT ADDED: setLogExcluded(boolean). Signal references it from
// androidx.core.telecom's CallAttributesUtils$Api36Point1PlusImpl, but the
// method exists in NO AOSP API surface -- absent from core/api/current.txt,
// system-current.txt, module-lib-current.txt and test-current.txt on main, and
// from the source on android14/15/16-release and main. The androidx class name
// gates it to API 36.1+, and we present a far lower SDK_INT, so that impl class
// is unreachable here exactly as it is on any device below 36.1. Adding the
// method would be inventing API that Android itself does not expose at the
// level we report.
package android.telecom;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

public final class CallAttributes implements Parcelable {

    /** @hide on AOSP: bundle keys Telecom uses to ferry these across. */
    public static final String CALL_CAPABILITIES_KEY = "TelecomCapabilities";
    public static final String DISPLAY_NAME_KEY = "DisplayName";
    public static final String CALLER_PID_KEY = "CallerPid";
    public static final String CALLER_UID_KEY = "CallerUid";

    public static final int DIRECTION_INCOMING = 1;
    public static final int DIRECTION_OUTGOING = 2;

    public static final int AUDIO_CALL = 1;
    public static final int VIDEO_CALL = 2;

    public static final int SUPPORTS_SET_INACTIVE = 1 << 1;
    public static final int SUPPORTS_STREAM = 1 << 2;
    public static final int SUPPORTS_TRANSFER = 1 << 3;
    public static final int SUPPORTS_VIDEO_CALLING = 1 << 4;

    private final PhoneAccountHandle mPhoneAccountHandle;
    private final CharSequence mDisplayName;
    private final Uri mAddress;
    private final int mDirection;
    private final int mCallType;
    private final int mCallCapabilities;

    private CallAttributes(PhoneAccountHandle phoneAccountHandle, CharSequence displayName,
            Uri address, int direction, int callType, int callCapabilities) {
        mPhoneAccountHandle = phoneAccountHandle;
        mDisplayName = displayName;
        mAddress = address;
        mDirection = direction;
        mCallType = callType;
        mCallCapabilities = callCapabilities;
    }

    public PhoneAccountHandle getPhoneAccountHandle() {
        return mPhoneAccountHandle;
    }

    public CharSequence getDisplayName() {
        return mDisplayName;
    }

    public Uri getAddress() {
        return mAddress;
    }

    public int getDirection() {
        return mDirection;
    }

    public int getCallType() {
        return mCallType;
    }

    public int getCallCapabilities() {
        return mCallCapabilities;
    }

    public static final class Builder {
        // AOSP's defaults: an audio call that can be set inactive.
        private static final int DEFAULT_CALL_TYPE = AUDIO_CALL;
        private static final int DEFAULT_CALL_CAPABILITIES = SUPPORTS_SET_INACTIVE;

        private final PhoneAccountHandle mPhoneAccountHandle;
        private final int mDirection;
        private final CharSequence mDisplayName;
        private final Uri mAddress;
        private int mCallType = DEFAULT_CALL_TYPE;
        private int mCallCapabilities = DEFAULT_CALL_CAPABILITIES;

        public Builder(PhoneAccountHandle phoneAccountHandle, int callDirection,
                CharSequence displayName, Uri address) {
            if (!isInRange(DIRECTION_INCOMING, DIRECTION_OUTGOING, callDirection)) {
                throw new IllegalArgumentException("CallDirection=[" + callDirection + "] is"
                        + " invalid. CallDirections value should be within ["
                        + DIRECTION_INCOMING + ", " + DIRECTION_OUTGOING + "]");
            }
            Objects.requireNonNull(phoneAccountHandle);
            Objects.requireNonNull(displayName);
            Objects.requireNonNull(address);
            mPhoneAccountHandle = phoneAccountHandle;
            mDirection = callDirection;
            mDisplayName = displayName;
            mAddress = address;
        }

        public Builder setCallType(int callType) {
            if (!isInRange(AUDIO_CALL, VIDEO_CALL, callType)) {
                throw new IllegalArgumentException("CallType=[" + callType + "] is invalid."
                        + " CallTypes value should be within [" + AUDIO_CALL + ", "
                        + VIDEO_CALL + "]");
            }
            mCallType = callType;
            return this;
        }

        public Builder setCallCapabilities(int callCapabilities) {
            mCallCapabilities = callCapabilities;
            return this;
        }

        public CallAttributes build() {
            return new CallAttributes(mPhoneAccountHandle, mDisplayName, mAddress, mDirection,
                    mCallType, mCallCapabilities);
        }

        private boolean isInRange(int floor, int ceiling, int value) {
            return value >= floor && value <= ceiling;
        }
    }

    @Override public int describeContents() {
        return 0;
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mPhoneAccountHandle, flags);
        dest.writeCharSequence(mDisplayName);
        dest.writeParcelable(mAddress, flags);
        dest.writeInt(mDirection);
        dest.writeInt(mCallType);
        dest.writeInt(mCallCapabilities);
    }

    public static final Creator<CallAttributes> CREATOR = new Creator<CallAttributes>() {
        @Override public CallAttributes createFromParcel(Parcel source) {
            PhoneAccountHandle handle =
                    source.readParcelable(PhoneAccountHandle.class.getClassLoader());
            CharSequence displayName = source.readCharSequence();
            Uri address = source.readParcelable(Uri.class.getClassLoader());
            int direction = source.readInt();
            int callType = source.readInt();
            int capabilities = source.readInt();
            return new CallAttributes(handle, displayName, address, direction, callType,
                    capabilities);
        }

        @Override public CallAttributes[] newArray(int size) {
            return new CallAttributes[size];
        }
    };

    @Override public String toString() {
        return "CallAttributes: [phoneAccountHandle: " + mPhoneAccountHandle + "], "
                + "[direction: " + mDirection + "], [callType: " + mCallType + "], "
                + "[callCapabilities: " + mCallCapabilities + "]";
    }

    @Override public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof CallAttributes)) return false;
        CallAttributes that = (CallAttributes) obj;
        return this.mDirection == that.mDirection
                && this.mCallType == that.mCallType
                && this.mCallCapabilities == that.mCallCapabilities
                && Objects.equals(this.mPhoneAccountHandle, that.mPhoneAccountHandle)
                && Objects.equals(this.mAddress, that.mAddress);
    }

    @Override public int hashCode() {
        return Objects.hash(mPhoneAccountHandle, mAddress, mDirection, mCallType,
                mCallCapabilities);
    }
}
