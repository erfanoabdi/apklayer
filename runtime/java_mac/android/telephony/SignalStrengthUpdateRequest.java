package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A request to be told when signal strength crosses given thresholds.
 *
 * Mac.apk has no radio, so TelephonyManager.setSignalStrengthUpdateRequest()
 * accepts the request and no threshold is ever crossed -- the same observable
 * behaviour as a device in airplane mode. The request object itself is a
 * faithful value type.
 *
 * Source: frameworks/base/telephony/java/android/telephony/SignalStrengthUpdateRequest.java
 */
public final class SignalStrengthUpdateRequest implements Parcelable {

    private final List<SignalThresholdInfo> mSignalThresholdInfos;
    private final boolean mIsReportingRequestedWhileIdle;

    private SignalStrengthUpdateRequest(List<SignalThresholdInfo> infos,
            boolean isReportingRequestedWhileIdle) {
        mSignalThresholdInfos = Collections.unmodifiableList(new ArrayList<>(infos));
        mIsReportingRequestedWhileIdle = isReportingRequestedWhileIdle;
    }

    public Collection<SignalThresholdInfo> getSignalThresholdInfos() {
        return mSignalThresholdInfos;
    }

    public boolean isReportingRequestedWhileIdle() { return mIsReportingRequestedWhileIdle; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mSignalThresholdInfos);
        dest.writeBoolean(mIsReportingRequestedWhileIdle);
    }

    private SignalStrengthUpdateRequest(Parcel in) {
        List<SignalThresholdInfo> infos = in.createTypedArrayList(SignalThresholdInfo.CREATOR);
        mSignalThresholdInfos = Collections.unmodifiableList(
                infos == null ? new ArrayList<SignalThresholdInfo>() : infos);
        mIsReportingRequestedWhileIdle = in.readBoolean();
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SignalStrengthUpdateRequest)) return false;
        SignalStrengthUpdateRequest other = (SignalStrengthUpdateRequest) o;
        return mSignalThresholdInfos.equals(other.mSignalThresholdInfos)
                && mIsReportingRequestedWhileIdle == other.mIsReportingRequestedWhileIdle;
    }

    @Override public int hashCode() {
        return Objects.hash(mSignalThresholdInfos, mIsReportingRequestedWhileIdle);
    }

    @Override public String toString() {
        return "SignalStrengthUpdateRequest{mSignalThresholdInfos=" + mSignalThresholdInfos
                + " mIsReportingRequestedWhileIdle=" + mIsReportingRequestedWhileIdle + "}";
    }

    public static final Creator<SignalStrengthUpdateRequest> CREATOR =
            new Creator<SignalStrengthUpdateRequest>() {
        @Override public SignalStrengthUpdateRequest createFromParcel(Parcel in) {
            return new SignalStrengthUpdateRequest(in);
        }
        @Override public SignalStrengthUpdateRequest[] newArray(int size) {
            return new SignalStrengthUpdateRequest[size];
        }
    };

    public static final class Builder {
        private List<SignalThresholdInfo> mSignalThresholdInfos = null;
        private boolean mIsReportingRequestedWhileIdle = false;

        public Builder() { }

        public Builder setSignalThresholdInfos(Collection<SignalThresholdInfo> infos) {
            Objects.requireNonNull(infos, "SignalThresholdInfo collection must not be null");
            if (infos.isEmpty()) {
                throw new IllegalArgumentException("SignalThresholdInfo collection is empty");
            }
            mSignalThresholdInfos = new ArrayList<>(infos);
            return this;
        }

        public Builder setReportingRequestedWhileIdle(boolean isReportingRequestedWhileIdle) {
            mIsReportingRequestedWhileIdle = isReportingRequestedWhileIdle;
            return this;
        }

        public SignalStrengthUpdateRequest build() {
            if (mSignalThresholdInfos == null) {
                throw new IllegalArgumentException("SignalThresholdInfo collection must be set");
            }
            return new SignalStrengthUpdateRequest(mSignalThresholdInfos,
                    mIsReportingRequestedWhileIdle);
        }
    }
}
