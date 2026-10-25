package android.telephony;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.telecom.PhoneAccountHandle;

/**
 * A parsed visual-voicemail SMS.
 *
 * There is no SMS radio on a Mac, so one of these is never delivered. Immutable
 * value type.
 *
 * Source: frameworks/base/telephony/java/android/telephony/VisualVoicemailSms.java
 */
public final class VisualVoicemailSms implements Parcelable {

    private final PhoneAccountHandle mPhoneAccountHandle;
    private final String mPrefix;
    private final Bundle mFields;
    private final String mMessageBody;

    private VisualVoicemailSms(PhoneAccountHandle handle, String prefix, Bundle fields,
            String messageBody) {
        mPhoneAccountHandle = handle;
        mPrefix = prefix;
        mFields = fields;
        mMessageBody = messageBody;
    }

    public PhoneAccountHandle getPhoneAccountHandle() { return mPhoneAccountHandle; }

    public String getPrefix() { return mPrefix; }

    public Bundle getFields() { return mFields; }

    public String getMessageBody() { return mMessageBody; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mPhoneAccountHandle, flags);
        dest.writeString(mPrefix);
        dest.writeBundle(mFields);
        dest.writeString(mMessageBody);
    }

    public static final Creator<VisualVoicemailSms> CREATOR = new Creator<VisualVoicemailSms>() {
        @Override public VisualVoicemailSms createFromParcel(Parcel in) {
            return new VisualVoicemailSms(
                    in.readParcelable(PhoneAccountHandle.class.getClassLoader(),
                            PhoneAccountHandle.class),
                    in.readString(), in.readBundle(), in.readString());
        }
        @Override public VisualVoicemailSms[] newArray(int size) {
            return new VisualVoicemailSms[size];
        }
    };

    /** @hide AOSP declares a Builder for the framework's own use. */
    public static final class Builder {
        private PhoneAccountHandle mPhoneAccountHandle;
        private String mPrefix;
        private Bundle mFields;
        private String mMessageBody;

        public VisualVoicemailSms build() {
            return new VisualVoicemailSms(mPhoneAccountHandle, mPrefix, mFields, mMessageBody);
        }

        public Builder setPhoneAccountHandle(PhoneAccountHandle handle) {
            mPhoneAccountHandle = handle;
            return this;
        }

        public Builder setPrefix(String prefix) { mPrefix = prefix; return this; }
        public Builder setFields(Bundle fields) { mFields = fields; return this; }
        public Builder setMessageBody(String messageBody) {
            mMessageBody = messageBody;
            return this;
        }
    }
}
