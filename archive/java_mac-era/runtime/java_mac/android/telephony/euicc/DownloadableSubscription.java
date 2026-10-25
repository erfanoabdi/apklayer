package android.telephony.euicc;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A subscription that could be downloaded onto an eUICC.
 *
 * Pure value type built from an activation code; nothing here needs an eUICC.
 * Downloading it is what needs one, and EuiccManager reports that honestly.
 *
 * Source: frameworks/base/telephony/java/android/telephony/euicc/DownloadableSubscription.java
 */
public final class DownloadableSubscription implements Parcelable {

    private final String mEncodedActivationCode;
    private String mConfirmationCode;

    private DownloadableSubscription(String encodedActivationCode) {
        mEncodedActivationCode = encodedActivationCode;
    }

    private DownloadableSubscription(Parcel in) {
        mEncodedActivationCode = in.readString();
        mConfirmationCode = in.readString();
    }

    public static DownloadableSubscription forActivationCode(String encodedActivationCode) {
        java.util.Objects.requireNonNull(encodedActivationCode, "encodedActivationCode");
        return new DownloadableSubscription(encodedActivationCode);
    }

    public String getEncodedActivationCode() { return mEncodedActivationCode; }

    public String getConfirmationCode() { return mConfirmationCode; }

    /** @hide */ public void setConfirmationCode(String code) { mConfirmationCode = code; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mEncodedActivationCode);
        dest.writeString(mConfirmationCode);
    }

    public static final Creator<DownloadableSubscription> CREATOR =
            new Creator<DownloadableSubscription>() {
        @Override public DownloadableSubscription createFromParcel(Parcel in) {
            return new DownloadableSubscription(in);
        }
        @Override public DownloadableSubscription[] newArray(int size) {
            return new DownloadableSubscription[size];
        }
    };

    /** AOSP declares a Builder, hidden from the public API but present. */
    public static final class Builder {
        private String mEncodedActivationCode;
        private String mConfirmationCode;

        public Builder() { }

        public Builder(DownloadableSubscription baseSubscription) {
            mEncodedActivationCode = baseSubscription.getEncodedActivationCode();
            mConfirmationCode = baseSubscription.getConfirmationCode();
        }

        public Builder setEncodedActivationCode(String value) {
            mEncodedActivationCode = value;
            return this;
        }

        public Builder setConfirmationCode(String value) {
            mConfirmationCode = value;
            return this;
        }

        public DownloadableSubscription build() {
            DownloadableSubscription sub = new DownloadableSubscription(mEncodedActivationCode);
            sub.mConfirmationCode = mConfirmationCode;
            return sub;
        }
    }
}
