package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Filter describing which incoming SMS should be treated as visual voicemail.
 *
 * There is no SMS radio on a Mac, so no message ever matches the filter and
 * TelephonyManager.setVisualVoicemailSmsFilterSettings() stores it without
 * effect. Faithful value type; note the three PUBLIC FINAL FIELDS, which are
 * part of AOSP's API surface rather than getters.
 *
 * Source: frameworks/base/telephony/java/android/telephony/VisualVoicemailSmsFilterSettings.java
 */
public final class VisualVoicemailSmsFilterSettings implements Parcelable {

    public static final int DESTINATION_PORT_ANY = -1;
    public static final int DESTINATION_PORT_DATA_SMS = -2;

    /** AOSP's DEFAULT_CLIENT_PREFIX. */
    private static final String DEFAULT_CLIENT_PREFIX = "//VVM";

    public final String clientPrefix;
    public final List<String> originatingNumbers;
    public final int destinationPort;

    private VisualVoicemailSmsFilterSettings(String clientPrefix, List<String> originatingNumbers,
            int destinationPort) {
        this.clientPrefix = clientPrefix;
        this.originatingNumbers = originatingNumbers;
        this.destinationPort = destinationPort;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(clientPrefix);
        dest.writeStringList(originatingNumbers);
        dest.writeInt(destinationPort);
    }

    public static final Creator<VisualVoicemailSmsFilterSettings> CREATOR =
            new Creator<VisualVoicemailSmsFilterSettings>() {
        @Override public VisualVoicemailSmsFilterSettings createFromParcel(Parcel in) {
            Builder builder = new Builder();
            builder.setClientPrefix(in.readString());
            List<String> numbers = in.createStringArrayList();
            builder.setOriginatingNumbers(numbers == null ? new ArrayList<String>() : numbers);
            builder.setDestinationPort(in.readInt());
            return builder.build();
        }
        @Override public VisualVoicemailSmsFilterSettings[] newArray(int size) {
            return new VisualVoicemailSmsFilterSettings[size];
        }
    };

    @Override public String toString() {
        return "VisualVoicemailSmsFilterSettings [clientPrefix=" + clientPrefix
                + ", originatingNumbers=" + originatingNumbers
                + ", destinationPort=" + destinationPort + "]";
    }

    /** AOSP declares Builder as a plain (non-final) static class. */
    public static class Builder {
        private String mClientPrefix = DEFAULT_CLIENT_PREFIX;
        private List<String> mOriginatingNumbers = Collections.emptyList();
        private int mDestinationPort = DESTINATION_PORT_ANY;

        public Builder() { }

        public VisualVoicemailSmsFilterSettings build() {
            return new VisualVoicemailSmsFilterSettings(mClientPrefix,
                    Collections.unmodifiableList(new ArrayList<>(mOriginatingNumbers)),
                    mDestinationPort);
        }

        public Builder setClientPrefix(String clientPrefix) {
            if (clientPrefix == null) throw new IllegalArgumentException("Client prefix cannot be null");
            mClientPrefix = clientPrefix;
            return this;
        }

        public Builder setOriginatingNumbers(List<String> originatingNumbers) {
            if (originatingNumbers == null) {
                throw new IllegalArgumentException("Originating numbers cannot be null");
            }
            mOriginatingNumbers = originatingNumbers;
            return this;
        }

        public Builder setDestinationPort(int destinationPort) {
            mDestinationPort = destinationPort;
            return this;
        }
    }
}
