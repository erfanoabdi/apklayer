package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Information about one UICC (SIM) card.
 *
 * There is no SIM slot on a Mac, so TelephonyManager.getUiccCardsInfo() returns
 * an empty list. Faithful immutable value type.
 *
 * Source: frameworks/base/telephony/java/android/telephony/UiccCardInfo.java
 */
public final class UiccCardInfo implements Parcelable {

    private final boolean mIsEuicc;
    private final int mCardId;
    private final String mEid;
    private final int mPhysicalSlotIndex;
    private final boolean mIsRemovable;
    private final boolean mIsMultipleEnabledProfilesSupported;
    private final List<UiccPortInfo> mPortList;

    /** @hide */
    public UiccCardInfo(boolean isEuicc, int cardId, String eid, int physicalSlotIndex,
            boolean isRemovable, boolean isMultipleEnabledProfilesSupported,
            List<UiccPortInfo> portList) {
        mIsEuicc = isEuicc;
        mCardId = cardId;
        mEid = eid;
        mPhysicalSlotIndex = physicalSlotIndex;
        mIsRemovable = isRemovable;
        mIsMultipleEnabledProfilesSupported = isMultipleEnabledProfilesSupported;
        mPortList = portList == null
                ? Collections.<UiccPortInfo>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(portList));
    }

    private UiccCardInfo(Parcel in) {
        mIsEuicc = in.readBoolean();
        mCardId = in.readInt();
        mEid = in.readString();
        mPhysicalSlotIndex = in.readInt();
        mIsRemovable = in.readBoolean();
        mIsMultipleEnabledProfilesSupported = in.readBoolean();
        List<UiccPortInfo> ports = in.createTypedArrayList(UiccPortInfo.CREATOR);
        mPortList = ports == null
                ? Collections.<UiccPortInfo>emptyList() : Collections.unmodifiableList(ports);
    }

    public boolean isEuicc() { return mIsEuicc; }

    public int getCardId() { return mCardId; }

    public String getEid() { return mEid; }

    /** @deprecated AOSP moved the ICCID onto UiccPortInfo in API 33. */
    @Deprecated public String getIccId() {
        return mPortList.isEmpty() ? null : mPortList.get(0).getIccId();
    }

    public int getPhysicalSlotIndex() { return mPhysicalSlotIndex; }

    /** @deprecated AOSP renamed this to {@link #getPhysicalSlotIndex()} in API 33. */
    @Deprecated public int getSlotIndex() { return mPhysicalSlotIndex; }

    public boolean isRemovable() { return mIsRemovable; }

    public boolean isMultipleEnabledProfilesSupported() {
        return mIsMultipleEnabledProfilesSupported;
    }

    public Collection<UiccPortInfo> getPorts() { return mPortList; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeBoolean(mIsEuicc);
        dest.writeInt(mCardId);
        dest.writeString(mEid);
        dest.writeInt(mPhysicalSlotIndex);
        dest.writeBoolean(mIsRemovable);
        dest.writeBoolean(mIsMultipleEnabledProfilesSupported);
        dest.writeTypedList(mPortList);
    }

    public static final Creator<UiccCardInfo> CREATOR = new Creator<UiccCardInfo>() {
        @Override public UiccCardInfo createFromParcel(Parcel in) { return new UiccCardInfo(in); }
        @Override public UiccCardInfo[] newArray(int size) { return new UiccCardInfo[size]; }
    };

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof UiccCardInfo)) return false;
        UiccCardInfo that = (UiccCardInfo) obj;
        return mIsEuicc == that.mIsEuicc && mCardId == that.mCardId
                && Objects.equals(mEid, that.mEid)
                && mPhysicalSlotIndex == that.mPhysicalSlotIndex
                && mIsRemovable == that.mIsRemovable
                && mIsMultipleEnabledProfilesSupported == that.mIsMultipleEnabledProfilesSupported
                && mPortList.equals(that.mPortList);
    }

    @Override public int hashCode() {
        return Objects.hash(mIsEuicc, mCardId, mEid, mPhysicalSlotIndex, mIsRemovable,
                mIsMultipleEnabledProfilesSupported, mPortList);
    }

    @Override public String toString() {
        return "UiccCardInfo (mIsEuicc=" + mIsEuicc + ", mCardId=" + mCardId
                + ", mPhysicalSlotIndex=" + mPhysicalSlotIndex + ")";
    }
}
