// android.telecom.DisconnectCause -- why a call ended, as a code plus optional
// user-visible label/description and a machine-readable reason.
//
// A REAL implementation: an immutable value object with no device dependency.
// Signal and Telegram both construct one and read getCode()/getReason() back
// on their own error paths, which works identically here.
//
// The code values are AOSP's, taken from frameworks/base/core/api/current.txt
// rather than from the source, because the source defines them by reference to
// TelecomProtoEnums (a generated proto class we do not have). current.txt
// carries the resolved literals, so this is the authoritative form.
//
// Mirrors telecomm/java/android/telecom/DisconnectCause.java.
package android.telecom;

import android.media.ToneGenerator;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

public final class DisconnectCause implements Parcelable {

    public static final int UNKNOWN = 0;
    public static final int ERROR = 1;
    public static final int LOCAL = 2;
    public static final int REMOTE = 3;
    public static final int CANCELED = 4;
    public static final int MISSED = 5;
    public static final int REJECTED = 6;
    public static final int BUSY = 7;
    public static final int RESTRICTED = 8;
    public static final int OTHER = 9;
    public static final int CONNECTION_MANAGER_NOT_SUPPORTED = 10;
    public static final int ANSWERED_ELSEWHERE = 11;
    public static final int CALL_PULLED = 12;

    public static final String REASON_WIFI_ON_BUT_WFC_OFF = "REASON_WIFI_ON_BUT_WFC_OFF";
    public static final String REASON_IMS_ACCESS_BLOCKED = "REASON_IMS_ACCESS_BLOCKED";
    public static final String REASON_EMULATING_SINGLE_CALL = "EMULATING_SINGLE_CALL";
    public static final String REASON_EMERGENCY_CALL_PLACED = "REASON_EMERGENCY_CALL_PLACED";

    private int mDisconnectCode;
    private CharSequence mDisconnectLabel;
    private CharSequence mDisconnectDescription;
    private String mDisconnectReason;
    private int mToneToPlay;

    public DisconnectCause(int code) {
        this(code, null, null, null, ToneGenerator.TONE_UNKNOWN);
    }

    public DisconnectCause(int code, String reason) {
        this(code, null, null, reason, ToneGenerator.TONE_UNKNOWN);
    }

    public DisconnectCause(int code, CharSequence label, CharSequence description, String reason) {
        this(code, label, description, reason, ToneGenerator.TONE_UNKNOWN);
    }

    public DisconnectCause(int code, CharSequence label, CharSequence description, String reason,
            int toneToPlay) {
        mDisconnectCode = code;
        mDisconnectLabel = label;
        mDisconnectDescription = description;
        mDisconnectReason = reason;
        mToneToPlay = toneToPlay;
    }

    public int getCode() {
        return mDisconnectCode;
    }

    public CharSequence getLabel() {
        return mDisconnectLabel;
    }

    public CharSequence getDescription() {
        return mDisconnectDescription;
    }

    public String getReason() {
        return mDisconnectReason;
    }

    /** The ToneGenerator tone to play, or TONE_UNKNOWN (-1) for none. */
    public int getTone() {
        return mToneToPlay;
    }

    public static final Creator<DisconnectCause> CREATOR = new Creator<DisconnectCause>() {
        @Override public DisconnectCause createFromParcel(Parcel source) {
            int code = source.readInt();
            CharSequence label = source.readCharSequence();
            CharSequence description = source.readCharSequence();
            String reason = source.readString();
            int tone = source.readInt();
            return new DisconnectCause(code, label, description, reason, tone);
        }

        @Override public DisconnectCause[] newArray(int size) {
            return new DisconnectCause[size];
        }
    };

    @Override public void writeToParcel(Parcel destination, int flags) {
        destination.writeInt(mDisconnectCode);
        destination.writeCharSequence(mDisconnectLabel);
        destination.writeCharSequence(mDisconnectDescription);
        destination.writeString(mDisconnectReason);
        destination.writeInt(mToneToPlay);
    }

    @Override public int describeContents() {
        return 0;
    }

    @Override public int hashCode() {
        return Objects.hashCode(mDisconnectCode)
                + Objects.hashCode(mDisconnectLabel)
                + Objects.hashCode(mDisconnectDescription)
                + Objects.hashCode(mDisconnectReason)
                + Objects.hashCode(mToneToPlay);
    }

    @Override public boolean equals(Object o) {
        if (o instanceof DisconnectCause) {
            DisconnectCause d = (DisconnectCause) o;
            return Objects.equals(mDisconnectCode, d.getCode())
                    && Objects.equals(mDisconnectLabel, d.getLabel())
                    && Objects.equals(mDisconnectDescription, d.getDescription())
                    && Objects.equals(mDisconnectReason, d.getReason())
                    && Objects.equals(mToneToPlay, d.getTone());
        }
        return false;
    }

    @Override public String toString() {
        String code = "";
        switch (mDisconnectCode) {
            case UNKNOWN: code = "UNKNOWN"; break;
            case ERROR: code = "ERROR"; break;
            case LOCAL: code = "LOCAL"; break;
            case REMOTE: code = "REMOTE"; break;
            case CANCELED: code = "CANCELED"; break;
            case MISSED: code = "MISSED"; break;
            case REJECTED: code = "REJECTED"; break;
            case BUSY: code = "BUSY"; break;
            case RESTRICTED: code = "RESTRICTED"; break;
            case OTHER: code = "OTHER"; break;
            case CONNECTION_MANAGER_NOT_SUPPORTED: code = "CONNECTION_MANAGER_NOT_SUPPORTED"; break;
            case ANSWERED_ELSEWHERE: code = "ANSWERED_ELSEWHERE"; break;
            case CALL_PULLED: code = "CALL_PULLED"; break;
            default: code = "invalid code: " + mDisconnectCode; break;
        }
        String label = mDisconnectLabel == null ? "" : mDisconnectLabel.toString();
        String description = mDisconnectDescription == null
                ? "" : mDisconnectDescription.toString();
        String reason = mDisconnectReason == null ? "" : mDisconnectReason;
        return "DisconnectCause [ Code: (" + code + ")"
                + " Label: (" + label + ")"
                + " Description: (" + description + ")"
                + " Reason: (" + reason + ")"
                + " Tone: (" + mToneToPlay + ") ]";
    }
}
