package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * One port on a UICC (SIM) card.
 *
 * There is no SIM slot on a Mac, so UiccCardInfo.getPorts() is never populated
 * by the framework. Faithful immutable value type.
 *
 * Source: frameworks/base/telephony/java/android/telephony/UiccPortInfo.java
 */
public final class UiccPortInfo implements Parcelable {

    public static final String ICCID_REDACTED = "FFFFFFFFFFFFFFFFFFFF";

    private final String mIccId;
    private final int mPortIndex;
    private final int mLogicalSlotIndex;
    private final boolean mIsActive;

    /** @hide */
    public UiccPortInfo(String iccId, int portIndex, int logicalSlotIndex, boolean isActive) {
        mIccId = iccId;
        mPortIndex = portIndex;
        mLogicalSlotIndex = logicalSlotIndex;
        mIsActive = isActive;
    }

    private UiccPortInfo(Parcel in) {
        mIccId = in.readString();
        mPortIndex = in.readInt();
        mLogicalSlotIndex = in.readInt();
        mIsActive = in.readBoolean();
    }

    public String getIccId() { return mIccId; }

    public int getPortIndex() { return mPortIndex; }

    public int getLogicalSlotIndex() { return mLogicalSlotIndex; }

    public boolean isActive() { return mIsActive; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mIccId);
        dest.writeInt(mPortIndex);
        dest.writeInt(mLogicalSlotIndex);
        dest.writeBoolean(mIsActive);
    }

    public static final Creator<UiccPortInfo> CREATOR = new Creator<UiccPortInfo>() {
        @Override public UiccPortInfo createFromParcel(Parcel in) { return new UiccPortInfo(in); }
        @Override public UiccPortInfo[] newArray(int size) { return new UiccPortInfo[size]; }
    };

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof UiccPortInfo)) return false;
        UiccPortInfo that = (UiccPortInfo) obj;
        return Objects.equals(mIccId, that.mIccId) && mPortIndex == that.mPortIndex
                && mLogicalSlotIndex == that.mLogicalSlotIndex && mIsActive == that.mIsActive;
    }

    @Override public int hashCode() {
        return Objects.hash(mIccId, mPortIndex, mLogicalSlotIndex, mIsActive);
    }

    @Override public String toString() {
        return "UiccPortInfo (isActive=" + mIsActive + ", iccId="
                + (mIccId == null ? "null" : ICCID_REDACTED) + ", portIndex=" + mPortIndex
                + ", mLogicalSlotIndex=" + mLogicalSlotIndex + ")";
    }
}
