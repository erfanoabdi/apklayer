package android.telephony.data;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * One APN (access point name) profile.
 *
 * Mac.apk has no SIM and no APN database, so nothing here is ever populated by
 * the framework -- PreciseDataConnectionState.getApnSetting() is null. The
 * value type + Builder are faithful so an app that constructs one links.
 *
 * Source: frameworks/base/telephony/java/android/telephony/data/ApnSetting.java
 */
public class ApnSetting implements Parcelable {

    public static final int AUTH_TYPE_CHAP = 2;
    public static final int AUTH_TYPE_NONE = 0;
    public static final int AUTH_TYPE_PAP = 1;
    public static final int AUTH_TYPE_PAP_OR_CHAP = 3;
    public static final int MVNO_TYPE_GID = 2;
    public static final int MVNO_TYPE_ICCID = 3;
    public static final int MVNO_TYPE_IMSI = 1;
    public static final int MVNO_TYPE_SPN = 0;
    public static final int PROTOCOL_IP = 0;
    public static final int PROTOCOL_IPV4V6 = 2;
    public static final int PROTOCOL_IPV6 = 1;
    public static final int PROTOCOL_NON_IP = 4;
    public static final int PROTOCOL_PPP = 3;
    public static final int PROTOCOL_UNSTRUCTURED = 5;
    public static final int TYPE_BIP = 8192;
    public static final int TYPE_CBS = 128;
    public static final int TYPE_DEFAULT = 17;
    public static final int TYPE_DUN = 8;
    public static final int TYPE_EMERGENCY = 512;
    public static final int TYPE_ENTERPRISE = 16384;
    public static final int TYPE_FOTA = 32;
    public static final int TYPE_HIPRI = 16;
    public static final int TYPE_IA = 256;
    public static final int TYPE_IMS = 64;
    public static final int TYPE_MCX = 1024;
    public static final int TYPE_MMS = 2;
    public static final int TYPE_OEM_PAID = 65536;
    public static final int TYPE_OEM_PRIVATE = 131072;
    public static final int TYPE_RCS = 32768;
    public static final int TYPE_SUPL = 4;
    public static final int TYPE_VSIM = 4096;
    public static final int TYPE_XCAP = 2048;

    /** @hide AOSP's "no mtu configured" sentinel. */
    public static final int UNSET_MTU = 0;

    private final int mId;
    private final String mOperatorNumeric;
    private final String mEntryName;
    private final String mApnName;
    private final String mProxyAddress;
    private final int mProxyPort;
    private final Uri mMmsc;
    private final String mMmsProxyAddress;
    private final int mMmsProxyPort;
    private final String mUser;
    private final String mPassword;
    private final int mAuthType;
    private final int mApnTypeBitmask;
    private final int mProtocol;
    private final int mRoamingProtocol;
    private final boolean mCarrierEnabled;
    private final int mNetworkTypeBitmask;
    private final int mProfileId;
    private final boolean mPersistent;
    private final int mMtuV4;
    private final int mMtuV6;
    private final int mMvnoType;
    private final int mCarrierId;
    private final boolean mAlwaysOn;

    private ApnSetting(Builder b) {
        mId = b.mId;
        mOperatorNumeric = b.mOperatorNumeric;
        mEntryName = b.mEntryName;
        mApnName = b.mApnName;
        mProxyAddress = b.mProxyAddress;
        mProxyPort = b.mProxyPort;
        mMmsc = b.mMmsc;
        mMmsProxyAddress = b.mMmsProxyAddress;
        mMmsProxyPort = b.mMmsProxyPort;
        mUser = b.mUser;
        mPassword = b.mPassword;
        mAuthType = b.mAuthType;
        mApnTypeBitmask = b.mApnTypeBitmask;
        mProtocol = b.mProtocol;
        mRoamingProtocol = b.mRoamingProtocol;
        mCarrierEnabled = b.mCarrierEnabled;
        mNetworkTypeBitmask = b.mNetworkTypeBitmask;
        mProfileId = b.mProfileId;
        mPersistent = b.mPersistent;
        mMtuV4 = b.mMtuV4;
        mMtuV6 = b.mMtuV6;
        mMvnoType = b.mMvnoType;
        mCarrierId = b.mCarrierId;
        mAlwaysOn = b.mAlwaysOn;
    }

    public int getId() { return mId; }
    public String getOperatorNumeric() { return mOperatorNumeric; }
    public String getEntryName() { return mEntryName; }
    public String getApnName() { return mApnName; }
    public String getProxyAddressAsString() { return mProxyAddress; }
    public int getProxyPort() { return mProxyPort; }
    public Uri getMmsc() { return mMmsc; }
    public String getMmsProxyAddressAsString() { return mMmsProxyAddress; }
    public int getMmsProxyPort() { return mMmsProxyPort; }
    public String getUser() { return mUser; }
    public String getPassword() { return mPassword; }
    public int getAuthType() { return mAuthType; }
    public int getApnTypeBitmask() { return mApnTypeBitmask; }
    public int getProtocol() { return mProtocol; }
    public int getRoamingProtocol() { return mRoamingProtocol; }
    public boolean isEnabled() { return mCarrierEnabled; }
    public int getNetworkTypeBitmask() { return mNetworkTypeBitmask; }
    public int getProfileId() { return mProfileId; }
    public boolean isPersistent() { return mPersistent; }
    public int getMtuV4() { return mMtuV4; }
    public int getMtuV6() { return mMtuV6; }
    public int getMvnoType() { return mMvnoType; }
    public int getCarrierId() { return mCarrierId; }
    public boolean isAlwaysOn() { return mAlwaysOn; }

    /** @deprecated AOSP replaced this by {@link #getProxyAddressAsString()} in API 29. */
    @Deprecated public InetAddress getProxyAddress() { return inetAddress(mProxyAddress); }

    /** @deprecated AOSP replaced this by {@link #getMmsProxyAddressAsString()} in API 29. */
    @Deprecated public InetAddress getMmsProxyAddress() { return inetAddress(mMmsProxyAddress); }

    private static InetAddress inetAddress(String host) {
        if (host == null) return null;
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            // AOSP's own helper returns null rather than propagating.
            return null;
        }
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeString(mOperatorNumeric);
        dest.writeString(mEntryName);
        dest.writeString(mApnName);
        dest.writeString(mProxyAddress);
        dest.writeInt(mProxyPort);
        dest.writeParcelable(mMmsc, flags);
        dest.writeString(mMmsProxyAddress);
        dest.writeInt(mMmsProxyPort);
        dest.writeString(mUser);
        dest.writeString(mPassword);
        dest.writeInt(mAuthType);
        dest.writeInt(mApnTypeBitmask);
        dest.writeInt(mProtocol);
        dest.writeInt(mRoamingProtocol);
        dest.writeBoolean(mCarrierEnabled);
        dest.writeInt(mNetworkTypeBitmask);
        dest.writeInt(mProfileId);
        dest.writeBoolean(mPersistent);
        dest.writeInt(mMtuV4);
        dest.writeInt(mMtuV6);
        dest.writeInt(mMvnoType);
        dest.writeInt(mCarrierId);
        dest.writeBoolean(mAlwaysOn);
    }

    public static final Creator<ApnSetting> CREATOR = new Creator<ApnSetting>() {
        @Override public ApnSetting createFromParcel(Parcel in) {
            Builder b = new Builder();
            b.mId = in.readInt();
            b.mOperatorNumeric = in.readString();
            b.mEntryName = in.readString();
            b.mApnName = in.readString();
            b.mProxyAddress = in.readString();
            b.mProxyPort = in.readInt();
            b.mMmsc = in.readParcelable(Uri.class.getClassLoader(), Uri.class);
            b.mMmsProxyAddress = in.readString();
            b.mMmsProxyPort = in.readInt();
            b.mUser = in.readString();
            b.mPassword = in.readString();
            b.mAuthType = in.readInt();
            b.mApnTypeBitmask = in.readInt();
            b.mProtocol = in.readInt();
            b.mRoamingProtocol = in.readInt();
            b.mCarrierEnabled = in.readBoolean();
            b.mNetworkTypeBitmask = in.readInt();
            b.mProfileId = in.readInt();
            b.mPersistent = in.readBoolean();
            b.mMtuV4 = in.readInt();
            b.mMtuV6 = in.readInt();
            b.mMvnoType = in.readInt();
            b.mCarrierId = in.readInt();
            b.mAlwaysOn = in.readBoolean();
            return b.build();
        }
        @Override public ApnSetting[] newArray(int size) { return new ApnSetting[size]; }
    };

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApnSetting)) return false;
        ApnSetting other = (ApnSetting) o;
        return mId == other.mId
                && Objects.equals(mOperatorNumeric, other.mOperatorNumeric)
                && Objects.equals(mEntryName, other.mEntryName)
                && Objects.equals(mApnName, other.mApnName)
                && Objects.equals(mProxyAddress, other.mProxyAddress)
                && mProxyPort == other.mProxyPort
                && Objects.equals(mMmsc, other.mMmsc)
                && mApnTypeBitmask == other.mApnTypeBitmask
                && mProtocol == other.mProtocol
                && mRoamingProtocol == other.mRoamingProtocol
                && mCarrierEnabled == other.mCarrierEnabled;
    }

    @Override public int hashCode() {
        return Objects.hash(mId, mOperatorNumeric, mEntryName, mApnName, mProxyAddress, mProxyPort,
                mMmsc, mApnTypeBitmask, mProtocol, mRoamingProtocol, mCarrierEnabled);
    }

    @Override public String toString() {
        return "ApnSetting{entryName=" + mEntryName + " apnName=" + mApnName + "}";
    }

    /** AOSP declares Builder as a plain (non-final) static class. */
    public static class Builder {
        private int mId = -1;
        private String mOperatorNumeric;
        private String mEntryName;
        private String mApnName;
        private String mProxyAddress;
        private int mProxyPort = -1;
        private Uri mMmsc;
        private String mMmsProxyAddress;
        private int mMmsProxyPort = -1;
        private String mUser;
        private String mPassword;
        private int mAuthType = -1;
        private int mApnTypeBitmask;
        private int mProtocol = -1;
        private int mRoamingProtocol = -1;
        private boolean mCarrierEnabled;
        private int mNetworkTypeBitmask;
        private int mProfileId;
        private boolean mPersistent;
        private int mMtuV4 = UNSET_MTU;
        private int mMtuV6 = UNSET_MTU;
        private int mMvnoType = -1;
        private int mCarrierId = -1;
        private boolean mAlwaysOn;

        public Builder() { }

        public Builder setEntryName(String entryName) { mEntryName = entryName; return this; }
        public Builder setApnName(String apnName) { mApnName = apnName; return this; }
        public Builder setProxyAddress(String proxy) { mProxyAddress = proxy; return this; }
        /** @deprecated AOSP replaced this by the String form in API 29. */
        @Deprecated public Builder setProxyAddress(InetAddress proxy) {
            mProxyAddress = proxy == null ? null : proxy.getHostAddress();
            return this;
        }
        public Builder setProxyPort(int port) { mProxyPort = port; return this; }
        public Builder setMmsc(Uri mmsc) { mMmsc = mmsc; return this; }
        public Builder setMmsProxyAddress(String mmsProxy) {
            mMmsProxyAddress = mmsProxy;
            return this;
        }
        /** @deprecated AOSP replaced this by the String form in API 29. */
        @Deprecated public Builder setMmsProxyAddress(InetAddress mmsProxy) {
            mMmsProxyAddress = mmsProxy == null ? null : mmsProxy.getHostAddress();
            return this;
        }
        public Builder setMmsProxyPort(int mmsPort) { mMmsProxyPort = mmsPort; return this; }
        public Builder setUser(String user) { mUser = user; return this; }
        public Builder setPassword(String password) { mPassword = password; return this; }
        public Builder setAuthType(int authType) { mAuthType = authType; return this; }
        public Builder setApnTypeBitmask(int apnTypeBitmask) {
            mApnTypeBitmask = apnTypeBitmask;
            return this;
        }
        public Builder setOperatorNumeric(String operatorNumeric) {
            mOperatorNumeric = operatorNumeric;
            return this;
        }
        public Builder setProtocol(int protocol) { mProtocol = protocol; return this; }
        public Builder setRoamingProtocol(int roamingProtocol) {
            mRoamingProtocol = roamingProtocol;
            return this;
        }
        public Builder setCarrierEnabled(boolean carrierEnabled) {
            mCarrierEnabled = carrierEnabled;
            return this;
        }
        public Builder setNetworkTypeBitmask(int networkTypeBitmask) {
            mNetworkTypeBitmask = networkTypeBitmask;
            return this;
        }
        public Builder setProfileId(int profileId) { mProfileId = profileId; return this; }
        public Builder setPersistent(boolean isPersistent) {
            mPersistent = isPersistent;
            return this;
        }
        public Builder setMtuV4(int mtuV4) { mMtuV4 = mtuV4; return this; }
        public Builder setMtuV6(int mtuV6) { mMtuV6 = mtuV6; return this; }
        public Builder setMvnoType(int mvnoType) { mMvnoType = mvnoType; return this; }
        public Builder setCarrierId(int carrierId) { mCarrierId = carrierId; return this; }
        public Builder setAlwaysOn(boolean alwaysOn) { mAlwaysOn = alwaysOn; return this; }

        public ApnSetting build() { return new ApnSetting(this); }
    }
}
