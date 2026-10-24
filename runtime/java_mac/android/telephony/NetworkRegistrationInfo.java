package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One (domain, transport) registration record inside a ServiceState.
 *
 * Mac.apk has no radio, so ServiceState.getNetworkRegistrationInfoList() is
 * empty. Faithful value type.
 *
 * Source: frameworks/base/telephony/java/android/telephony/NetworkRegistrationInfo.java
 */
public final class NetworkRegistrationInfo implements Parcelable {

    public static final int DOMAIN_CS = 1;
    public static final int DOMAIN_CS_PS = 3;
    public static final int DOMAIN_PS = 2;
    public static final int DOMAIN_UNKNOWN = 0;
    public static final int NR_STATE_CONNECTED = 3;
    public static final int NR_STATE_NONE = 0;
    public static final int NR_STATE_NOT_RESTRICTED = 2;
    public static final int NR_STATE_RESTRICTED = 1;
    public static final int SERVICE_TYPE_DATA = 2;
    public static final int SERVICE_TYPE_EMERGENCY = 5;
    public static final int SERVICE_TYPE_MMS = 6;
    public static final int SERVICE_TYPE_SMS = 3;
    public static final int SERVICE_TYPE_UNKNOWN = 0;
    public static final int SERVICE_TYPE_VIDEO = 4;
    public static final int SERVICE_TYPE_VOICE = 1;

    /** @hide AOSP's internal registration states. */
    public static final int REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING = 0;
    /** @hide */ public static final int REGISTRATION_STATE_HOME = 1;
    /** @hide */ public static final int REGISTRATION_STATE_NOT_REGISTERED_SEARCHING = 2;
    /** @hide */ public static final int REGISTRATION_STATE_DENIED = 3;
    /** @hide */ public static final int REGISTRATION_STATE_UNKNOWN = 4;
    /** @hide */ public static final int REGISTRATION_STATE_ROAMING = 5;

    private final int mDomain;
    private final int mTransportType;
    private final int mRegistrationState;
    private final int mAccessNetworkTechnology;
    private final int mRejectCause;
    private final boolean mEmergencyOnly;
    private final List<Integer> mAvailableServices;
    private final CellIdentity mCellIdentity;
    private final String mRegisteredPlmn;
    private final boolean mIsNonTerrestrialNetwork;

    /** @hide */
    public NetworkRegistrationInfo(int domain, int transportType, int registrationState,
            int accessNetworkTechnology, int rejectCause, boolean emergencyOnly,
            List<Integer> availableServices, CellIdentity cellIdentity, String rplmn,
            boolean isNonTerrestrialNetwork) {
        mDomain = domain;
        mTransportType = transportType;
        mRegistrationState = registrationState;
        mAccessNetworkTechnology = accessNetworkTechnology;
        mRejectCause = rejectCause;
        mEmergencyOnly = emergencyOnly;
        mAvailableServices = availableServices == null
                ? Collections.<Integer>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(availableServices));
        mCellIdentity = cellIdentity;
        mRegisteredPlmn = rplmn;
        mIsNonTerrestrialNetwork = isNonTerrestrialNetwork;
    }

    private NetworkRegistrationInfo(Parcel source) {
        mDomain = source.readInt();
        mTransportType = source.readInt();
        mRegistrationState = source.readInt();
        mAccessNetworkTechnology = source.readInt();
        mRejectCause = source.readInt();
        mEmergencyOnly = source.readBoolean();
        int n = source.readInt();
        List<Integer> services = new ArrayList<>();
        for (int i = 0; i < n; i++) services.add(source.readInt());
        mAvailableServices = Collections.unmodifiableList(services);
        mCellIdentity = source.readInt() == 1
                ? CellIdentity.CREATOR.createFromParcel(source) : null;
        mRegisteredPlmn = source.readString();
        mIsNonTerrestrialNetwork = source.readBoolean();
    }

    public int getDomain() { return mDomain; }
    public int getTransportType() { return mTransportType; }
    public int getAccessNetworkTechnology() { return mAccessNetworkTechnology; }
    public int getRejectCause() { return mRejectCause; }
    public CellIdentity getCellIdentity() { return mCellIdentity; }
    public String getRegisteredPlmn() { return mRegisteredPlmn; }
    public List<Integer> getAvailableServices() { return mAvailableServices; }
    public boolean isNonTerrestrialNetwork() { return mIsNonTerrestrialNetwork; }

    public boolean isNetworkRegistered() {
        return mRegistrationState == REGISTRATION_STATE_HOME
                || mRegistrationState == REGISTRATION_STATE_ROAMING;
    }

    public boolean isNetworkRoaming() { return mRegistrationState == REGISTRATION_STATE_ROAMING; }

    public boolean isNetworkSearching() {
        return mRegistrationState == REGISTRATION_STATE_NOT_REGISTERED_SEARCHING;
    }

    /** @deprecated AOSP renamed this to {@link #isNetworkRegistered()} in API 33. */
    @Deprecated public boolean isRegistered() { return isNetworkRegistered(); }

    /** @deprecated AOSP renamed this to {@link #isNetworkRoaming()} in API 33. */
    @Deprecated public boolean isRoaming() { return isNetworkRoaming(); }

    /** @deprecated AOSP renamed this to {@link #isNetworkSearching()} in API 33. */
    @Deprecated public boolean isSearching() { return isNetworkSearching(); }

    /** @hide */ public boolean isEmergencyEnabled() { return mEmergencyOnly; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mDomain);
        dest.writeInt(mTransportType);
        dest.writeInt(mRegistrationState);
        dest.writeInt(mAccessNetworkTechnology);
        dest.writeInt(mRejectCause);
        dest.writeBoolean(mEmergencyOnly);
        dest.writeInt(mAvailableServices.size());
        for (Integer service : mAvailableServices) dest.writeInt(service);
        if (mCellIdentity != null) {
            dest.writeInt(1);
            mCellIdentity.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        dest.writeString(mRegisteredPlmn);
        dest.writeBoolean(mIsNonTerrestrialNetwork);
    }

    public static final Creator<NetworkRegistrationInfo> CREATOR =
            new Creator<NetworkRegistrationInfo>() {
        @Override public NetworkRegistrationInfo createFromParcel(Parcel source) {
            return new NetworkRegistrationInfo(source);
        }
        @Override public NetworkRegistrationInfo[] newArray(int size) {
            return new NetworkRegistrationInfo[size];
        }
    };

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkRegistrationInfo)) return false;
        NetworkRegistrationInfo other = (NetworkRegistrationInfo) o;
        return mDomain == other.mDomain && mTransportType == other.mTransportType
                && mRegistrationState == other.mRegistrationState
                && mAccessNetworkTechnology == other.mAccessNetworkTechnology
                && mRejectCause == other.mRejectCause
                && mEmergencyOnly == other.mEmergencyOnly
                && mAvailableServices.equals(other.mAvailableServices)
                && Objects.equals(mCellIdentity, other.mCellIdentity)
                && Objects.equals(mRegisteredPlmn, other.mRegisteredPlmn)
                && mIsNonTerrestrialNetwork == other.mIsNonTerrestrialNetwork;
    }

    @Override public int hashCode() {
        return Objects.hash(mDomain, mTransportType, mRegistrationState, mAccessNetworkTechnology,
                mRejectCause, mEmergencyOnly, mAvailableServices, mCellIdentity, mRegisteredPlmn,
                mIsNonTerrestrialNetwork);
    }

    @Override public String toString() {
        return "NetworkRegistrationInfo{domain=" + mDomain + " transportType=" + mTransportType
                + " registrationState=" + mRegistrationState + "}";
    }
}
