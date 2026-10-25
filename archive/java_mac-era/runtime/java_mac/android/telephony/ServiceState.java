package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Snapshot of the device's telephony service state.
 *
 * Mac.apk hosts no radio, so a default-constructed ServiceState is
 * OUT_OF_SERVICE with no operator, no cell bandwidths, no CDMA ids and an empty
 * registration list. Every one of those is AOSP's own "nothing to report"
 * value, not a made-up network.
 *
 * Source: frameworks/base/telephony/java/android/telephony/ServiceState.java
 */
public class ServiceState implements Parcelable {

    public static final int STATE_IN_SERVICE = 0;
    public static final int STATE_OUT_OF_SERVICE = 1;
    public static final int STATE_EMERGENCY_ONLY = 2;
    public static final int STATE_POWER_OFF = 3;

    public static final int DUPLEX_MODE_UNKNOWN = 0;
    public static final int DUPLEX_MODE_FDD = 1;
    public static final int DUPLEX_MODE_TDD = 2;

    /** AOSP's sentinel for an unreported CDMA system/network id. */
    public static final int UNKNOWN_ID = -1;

    private int mVoiceRegState = STATE_OUT_OF_SERVICE;
    private int mDataRegState = STATE_OUT_OF_SERVICE;
    private boolean mRoaming;
    private boolean mIsManualNetworkSelection;
    private String mOperatorAlphaLong;
    private String mOperatorAlphaShort;
    private String mOperatorNumeric;
    private int mChannelNumber = -1;
    private int[] mCellBandwidths = new int[0];
    private int mCdmaSystemId = UNKNOWN_ID;
    private int mCdmaNetworkId = UNKNOWN_ID;
    private boolean mIsUsingNonTerrestrialNetwork;
    private final List<NetworkRegistrationInfo> mNetworkRegistrationInfos = new ArrayList<>();

    public ServiceState() { }

    public ServiceState(ServiceState s) { copyFrom(s); }

    /** @deprecated AOSP deprecated the Parcel constructor in API 31. */
    @Deprecated
    public ServiceState(Parcel in) {
        mVoiceRegState = in.readInt();
        mDataRegState = in.readInt();
        mRoaming = in.readBoolean();
        mIsManualNetworkSelection = in.readBoolean();
        mOperatorAlphaLong = in.readString();
        mOperatorAlphaShort = in.readString();
        mOperatorNumeric = in.readString();
        mChannelNumber = in.readInt();
        int[] bw = in.createIntArray();
        mCellBandwidths = bw == null ? new int[0] : bw;
        mCdmaSystemId = in.readInt();
        mCdmaNetworkId = in.readInt();
        mIsUsingNonTerrestrialNetwork = in.readBoolean();
        List<NetworkRegistrationInfo> nris =
                in.createTypedArrayList(NetworkRegistrationInfo.CREATOR);
        if (nris != null) mNetworkRegistrationInfos.addAll(nris);
    }

    protected void copyFrom(ServiceState s) {
        mVoiceRegState = s.mVoiceRegState;
        mDataRegState = s.mDataRegState;
        mRoaming = s.mRoaming;
        mIsManualNetworkSelection = s.mIsManualNetworkSelection;
        mOperatorAlphaLong = s.mOperatorAlphaLong;
        mOperatorAlphaShort = s.mOperatorAlphaShort;
        mOperatorNumeric = s.mOperatorNumeric;
        mChannelNumber = s.mChannelNumber;
        mCellBandwidths = s.mCellBandwidths == null ? new int[0] : s.mCellBandwidths.clone();
        mCdmaSystemId = s.mCdmaSystemId;
        mCdmaNetworkId = s.mCdmaNetworkId;
        mIsUsingNonTerrestrialNetwork = s.mIsUsingNonTerrestrialNetwork;
        mNetworkRegistrationInfos.clear();
        mNetworkRegistrationInfos.addAll(s.mNetworkRegistrationInfos);
    }

    public int getState() { return mVoiceRegState; }
    /** @hide */ public int getVoiceRegState() { return mVoiceRegState; }
    /** @hide */ public int getDataRegState() { return mDataRegState; }

    public void setState(int state) { mVoiceRegState = state; }

    public void setStateOutOfService() {
        mVoiceRegState = STATE_OUT_OF_SERVICE;
        mDataRegState = STATE_OUT_OF_SERVICE;
    }

    public void setStateOff() {
        mVoiceRegState = STATE_POWER_OFF;
        mDataRegState = STATE_POWER_OFF;
    }

    public boolean getRoaming() { return mRoaming; }
    public void setRoaming(boolean roaming) { mRoaming = roaming; }

    public boolean getIsManualSelection() { return mIsManualNetworkSelection; }
    public void setIsManualSelection(boolean isManual) { mIsManualNetworkSelection = isManual; }

    public String getOperatorAlphaLong() { return mOperatorAlphaLong; }
    public String getOperatorAlphaShort() { return mOperatorAlphaShort; }
    public String getOperatorNumeric() { return mOperatorNumeric; }

    public void setOperatorName(String longName, String shortName, String numeric) {
        mOperatorAlphaLong = longName;
        mOperatorAlphaShort = shortName;
        mOperatorNumeric = numeric;
    }

    /** AOSP: true only while actively scanning for a network. Nothing scans here. */
    public boolean isSearching() {
        for (NetworkRegistrationInfo nri : mNetworkRegistrationInfos) {
            if (nri.getDomain() == NetworkRegistrationInfo.DOMAIN_CS
                    && nri.isNetworkSearching()) {
                return true;
            }
        }
        return false;
    }

    public int getChannelNumber() { return mChannelNumber; }

    /** AOSP returns a copy; an empty array means "none reported". */
    public int[] getCellBandwidths() { return mCellBandwidths.clone(); }

    public int getDuplexMode() { return DUPLEX_MODE_UNKNOWN; }

    public int getCdmaSystemId() { return mCdmaSystemId; }

    public int getCdmaNetworkId() { return mCdmaNetworkId; }

    public boolean isUsingNonTerrestrialNetwork() { return mIsUsingNonTerrestrialNetwork; }

    public List<NetworkRegistrationInfo> getNetworkRegistrationInfoList() {
        return Collections.unmodifiableList(new ArrayList<>(mNetworkRegistrationInfos));
    }

    /** @hide */
    public void addNetworkRegistrationInfo(NetworkRegistrationInfo nri) {
        if (nri != null) mNetworkRegistrationInfos.add(nri);
    }

    @Override public String toString() {
        return "{mVoiceRegState=" + mVoiceRegState + ", mDataRegState=" + mDataRegState
                + ", mRoaming=" + mRoaming + ", mOperatorAlphaLong=" + mOperatorAlphaLong + "}";
    }

    @Override public int hashCode() {
        return Objects.hash(mVoiceRegState, mDataRegState, mRoaming, mOperatorAlphaLong,
                mOperatorAlphaShort, mOperatorNumeric);
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof ServiceState)) return false;
        ServiceState s = (ServiceState) o;
        return mVoiceRegState == s.mVoiceRegState && mDataRegState == s.mDataRegState
                && mRoaming == s.mRoaming
                && mIsManualNetworkSelection == s.mIsManualNetworkSelection
                && Objects.equals(mOperatorAlphaLong, s.mOperatorAlphaLong)
                && Objects.equals(mOperatorAlphaShort, s.mOperatorAlphaShort)
                && Objects.equals(mOperatorNumeric, s.mOperatorNumeric);
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mVoiceRegState);
        dest.writeInt(mDataRegState);
        dest.writeBoolean(mRoaming);
        dest.writeBoolean(mIsManualNetworkSelection);
        dest.writeString(mOperatorAlphaLong);
        dest.writeString(mOperatorAlphaShort);
        dest.writeString(mOperatorNumeric);
        dest.writeInt(mChannelNumber);
        dest.writeIntArray(mCellBandwidths);
        dest.writeInt(mCdmaSystemId);
        dest.writeInt(mCdmaNetworkId);
        dest.writeBoolean(mIsUsingNonTerrestrialNetwork);
        dest.writeTypedList(mNetworkRegistrationInfos);
    }

    public static final Creator<ServiceState> CREATOR = new Creator<ServiceState>() {
        @Override public ServiceState createFromParcel(Parcel in) { return new ServiceState(in); }
        @Override public ServiceState[] newArray(int size) { return new ServiceState[size]; }
    };
}
