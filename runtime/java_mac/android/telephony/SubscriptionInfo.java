// android.telephony.SubscriptionInfo
//
// A value type describing one SIM subscription. Mac.apk has no SIM, so
// SubscriptionManager never hands one out; the type + getters must resolve for
// subscription-enumerating code, and everything a caller reads back is what it
// (or a parcel) put in.
//
// Source: frameworks/base/telephony/java/android/telephony/SubscriptionInfo.java
package android.telephony;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class SubscriptionInfo implements Parcelable {

    /** @hide AOSP's SubscriptionManager.SUBSCRIPTION_TYPE_LOCAL_SIM. */
    private static final int SUBSCRIPTION_TYPE_LOCAL_SIM = 0;

    private final int mId;
    private final String mIccId;
    private final int mSimSlotIndex;
    private final CharSequence mDisplayName;
    private final CharSequence mCarrierName;
    private final int mIconTint;
    private final String mNumber;
    private final int mDataRoaming;
    private final String mMcc;
    private final String mMnc;
    private final String mCountryIso;
    private final boolean mIsEmbedded;
    private final int mCardId;
    private final boolean mIsOpportunistic;
    private final ParcelUuid mGroupUuid;
    private final int mCarrierId;
    private final int mSubscriptionType;
    private final int mUsageSetting;
    private final int mPortIndex;
    private final boolean mIsOnlyNonTerrestrialNetwork;
    private final Set<Integer> mServiceCapabilities;

    public SubscriptionInfo() {
        this(-1, "", -1, "", "", 0, 0);
    }

    /** @hide Legacy convenience constructor kept for framework/test callers. */
    public SubscriptionInfo(int id, String iccId, int simSlotIndex, CharSequence displayName,
            CharSequence carrierName, int mcc, int mnc) {
        mId = id;
        mIccId = iccId;
        mSimSlotIndex = simSlotIndex;
        mDisplayName = displayName;
        mCarrierName = carrierName;
        mIconTint = 0;
        mNumber = "";
        mDataRoaming = 0;
        mMcc = String.format(Locale.US, "%03d", mcc);
        mMnc = String.format(Locale.US, "%02d", mnc);
        mCountryIso = "";
        mIsEmbedded = false;
        mCardId = -1;
        mIsOpportunistic = false;
        mGroupUuid = null;
        mCarrierId = TelephonyManager.UNKNOWN_CARRIER_ID;
        mSubscriptionType = SUBSCRIPTION_TYPE_LOCAL_SIM;
        mUsageSetting = SubscriptionManager.USAGE_SETTING_UNKNOWN;
        mPortIndex = 0;
        mIsOnlyNonTerrestrialNetwork = false;
        mServiceCapabilities = Collections.emptySet();
    }

    private SubscriptionInfo(Parcel source) {
        mId = source.readInt();
        mIccId = source.readString();
        mSimSlotIndex = source.readInt();
        mDisplayName = source.readString();
        mCarrierName = source.readString();
        mIconTint = source.readInt();
        mNumber = source.readString();
        mDataRoaming = source.readInt();
        mMcc = source.readString();
        mMnc = source.readString();
        mCountryIso = source.readString();
        mIsEmbedded = source.readBoolean();
        mCardId = source.readInt();
        mIsOpportunistic = source.readBoolean();
        String uuid = source.readString();
        mGroupUuid = uuid == null ? null : ParcelUuid.fromString(uuid);
        mCarrierId = source.readInt();
        mSubscriptionType = source.readInt();
        mUsageSetting = source.readInt();
        mPortIndex = source.readInt();
        mIsOnlyNonTerrestrialNetwork = source.readBoolean();
        int n = source.readInt();
        Set<Integer> caps = new LinkedHashSet<>();
        for (int i = 0; i < n; i++) caps.add(source.readInt());
        mServiceCapabilities = Collections.unmodifiableSet(caps);
    }

    public int getSubscriptionId() { return mId; }
    public String getIccId() { return mIccId; }
    public int getSimSlotIndex() { return mSimSlotIndex; }
    public CharSequence getDisplayName() { return mDisplayName; }
    public CharSequence getCarrierName() { return mCarrierName; }
    public int getIconTint() { return mIconTint; }
    public int getDataRoaming() { return mDataRoaming; }
    public String getCountryIso() { return mCountryIso; }
    public boolean isEmbedded() { return mIsEmbedded; }
    public int getCardId() { return mCardId; }
    public boolean isOpportunistic() { return mIsOpportunistic; }
    public ParcelUuid getGroupUuid() { return mGroupUuid; }
    public int getCarrierId() { return mCarrierId; }
    public int getSubscriptionType() { return mSubscriptionType; }
    public int getUsageSetting() { return mUsageSetting; }
    public int getPortIndex() { return mPortIndex; }
    public boolean isOnlyNonTerrestrialNetwork() { return mIsOnlyNonTerrestrialNetwork; }
    public Set<Integer> getServiceCapabilities() { return mServiceCapabilities; }

    /** @deprecated AOSP replaced this by SubscriptionManager.getPhoneNumber() in API 33. */
    @Deprecated public String getNumber() { return mNumber; }

    public String getMccString() { return mMcc; }
    public String getMncString() { return mMnc; }

    /** @deprecated AOSP replaced the int MCC by {@link #getMccString()} in API 29. */
    @Deprecated public int getMcc() { return parseOrZero(mMcc); }

    /** @deprecated AOSP replaced the int MNC by {@link #getMncString()} in API 29. */
    @Deprecated public int getMnc() { return parseOrZero(mMnc); }

    private static int parseOrZero(String s) {
        try { return s == null ? 0 : Integer.parseInt(s); }
        catch (NumberFormatException e) { return 0; }
    }

    /**
     * AOSP renders the SIM's colour-tinted icon. There is no SIM and therefore
     * no icon, and AOSP itself returns null when the resources are unavailable.
     * Drawing a fabricated coloured square would be worse than nothing.
     */
    public Bitmap createIconBitmap(Context context) { return null; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeString(mIccId);
        dest.writeInt(mSimSlotIndex);
        dest.writeString(mDisplayName == null ? null : mDisplayName.toString());
        dest.writeString(mCarrierName == null ? null : mCarrierName.toString());
        dest.writeInt(mIconTint);
        dest.writeString(mNumber);
        dest.writeInt(mDataRoaming);
        dest.writeString(mMcc);
        dest.writeString(mMnc);
        dest.writeString(mCountryIso);
        dest.writeBoolean(mIsEmbedded);
        dest.writeInt(mCardId);
        dest.writeBoolean(mIsOpportunistic);
        dest.writeString(mGroupUuid == null ? null : mGroupUuid.toString());
        dest.writeInt(mCarrierId);
        dest.writeInt(mSubscriptionType);
        dest.writeInt(mUsageSetting);
        dest.writeInt(mPortIndex);
        dest.writeBoolean(mIsOnlyNonTerrestrialNetwork);
        dest.writeInt(mServiceCapabilities.size());
        for (Integer cap : mServiceCapabilities) dest.writeInt(cap);
    }

    public static final Creator<SubscriptionInfo> CREATOR = new Creator<SubscriptionInfo>() {
        @Override public SubscriptionInfo createFromParcel(Parcel source) {
            return new SubscriptionInfo(source);
        }
        @Override public SubscriptionInfo[] newArray(int size) { return new SubscriptionInfo[size]; }
    };

    @Override public int hashCode() {
        return Objects.hash(mId, mIccId, mSimSlotIndex, mCarrierId);
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SubscriptionInfo)) return false;
        SubscriptionInfo other = (SubscriptionInfo) obj;
        return mId == other.mId && Objects.equals(mIccId, other.mIccId)
                && mSimSlotIndex == other.mSimSlotIndex && mCarrierId == other.mCarrierId;
    }

    @Override public String toString() {
        return "SubscriptionInfo{id=" + mId + " simSlotIndex=" + mSimSlotIndex
                + " displayName=" + mDisplayName + "}";
    }
}
