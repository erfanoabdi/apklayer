package android.telephony.data;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The device's 5G network-slicing configuration.
 *
 * Mac.apk has no 5G modem, so TelephonyManager.getNetworkSlicingConfiguration()
 * delivers a default-constructed instance: no URSP rules and no slices. That is
 * the same object AOSP hands back before the modem has reported.
 *
 * Source: frameworks/base/telephony/java/android/telephony/data/NetworkSlicingConfig.java
 */
public final class NetworkSlicingConfig implements Parcelable {

    private final List<UrspRule> mUrspRules;
    private final List<NetworkSliceInfo> mSliceInfo;

    public NetworkSlicingConfig() {
        mUrspRules = Collections.emptyList();
        mSliceInfo = Collections.emptyList();
    }

    /** @hide */
    public NetworkSlicingConfig(List<UrspRule> urspRules, List<NetworkSliceInfo> sliceInfo) {
        mUrspRules = urspRules == null
                ? Collections.<UrspRule>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(urspRules));
        mSliceInfo = sliceInfo == null
                ? Collections.<NetworkSliceInfo>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(sliceInfo));
    }

    private NetworkSlicingConfig(Parcel p) {
        List<UrspRule> rules = p.createTypedArrayList(UrspRule.CREATOR);
        mUrspRules = rules == null
                ? Collections.<UrspRule>emptyList() : Collections.unmodifiableList(rules);
        List<NetworkSliceInfo> slices = p.createTypedArrayList(NetworkSliceInfo.CREATOR);
        mSliceInfo = slices == null
                ? Collections.<NetworkSliceInfo>emptyList() : Collections.unmodifiableList(slices);
    }

    public List<UrspRule> getUrspRules() { return mUrspRules; }

    public List<NetworkSliceInfo> getSliceInfo() { return mSliceInfo; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(mUrspRules);
        dest.writeTypedList(mSliceInfo);
    }

    public static final Creator<NetworkSlicingConfig> CREATOR = new Creator<NetworkSlicingConfig>() {
        @Override public NetworkSlicingConfig createFromParcel(Parcel in) {
            return new NetworkSlicingConfig(in);
        }
        @Override public NetworkSlicingConfig[] newArray(int size) {
            return new NetworkSlicingConfig[size];
        }
    };

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkSlicingConfig)) return false;
        NetworkSlicingConfig that = (NetworkSlicingConfig) o;
        return mUrspRules.equals(that.mUrspRules) && mSliceInfo.equals(that.mSliceInfo);
    }

    @Override public int hashCode() { return Objects.hash(mUrspRules, mSliceInfo); }

    @Override public String toString() {
        return "{.urspRules = " + mUrspRules + ", .sliceInfo = " + mSliceInfo + "}";
    }
}
