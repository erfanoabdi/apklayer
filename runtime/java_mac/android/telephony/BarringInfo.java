package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Which services the network is barring.
 *
 * Mac.apk has no network, so BarringInfo is never delivered. A
 * default-constructed instance reports BARRING_TYPE_UNKNOWN for every service,
 * which is AOSP's own "the network has not told us" value -- NOT
 * BARRING_TYPE_NONE, which would positively claim nothing is barred.
 *
 * Source: frameworks/base/telephony/java/android/telephony/BarringInfo.java
 */
public final class BarringInfo implements Parcelable {

    public static final int BARRING_SERVICE_TYPE_CS_SERVICE = 0;
    public static final int BARRING_SERVICE_TYPE_PS_SERVICE = 1;
    public static final int BARRING_SERVICE_TYPE_CS_VOICE = 2;
    public static final int BARRING_SERVICE_TYPE_MO_SIGNALLING = 3;
    public static final int BARRING_SERVICE_TYPE_MO_DATA = 4;
    public static final int BARRING_SERVICE_TYPE_CS_FALLBACK = 5;
    public static final int BARRING_SERVICE_TYPE_MMTEL_VOICE = 6;
    public static final int BARRING_SERVICE_TYPE_MMTEL_VIDEO = 7;
    public static final int BARRING_SERVICE_TYPE_EMERGENCY = 8;
    public static final int BARRING_SERVICE_TYPE_SMS = 9;

    private static final BarringServiceInfo BARRING_SERVICE_INFO_UNKNOWN =
            new BarringServiceInfo(BarringServiceInfo.BARRING_TYPE_UNKNOWN, false, 0, 0);

    private final CellIdentity mCellIdentity;
    private final Map<Integer, BarringServiceInfo> mBarringServiceInfos;

    /** @hide */
    public BarringInfo() {
        mCellIdentity = null;
        mBarringServiceInfos = new HashMap<>();
    }

    /** @hide */
    public BarringInfo(CellIdentity cellIdentity, Map<Integer, BarringServiceInfo> infos) {
        mCellIdentity = cellIdentity;
        mBarringServiceInfos = infos == null ? new HashMap<Integer, BarringServiceInfo>()
                : new HashMap<>(infos);
    }

    private BarringInfo(Parcel p) {
        mCellIdentity = p.readInt() == 1 ? CellIdentity.CREATOR.createFromParcel(p) : null;
        mBarringServiceInfos = new HashMap<>();
        int n = p.readInt();
        for (int i = 0; i < n; i++) {
            int key = p.readInt();
            mBarringServiceInfos.put(key, BarringServiceInfo.CREATOR.createFromParcel(p));
        }
    }

    /** AOSP is @NonNull and returns an UNKNOWN record for an unreported service. */
    public BarringServiceInfo getBarringServiceInfo(int service) {
        BarringServiceInfo bsi = mBarringServiceInfos.get(service);
        return bsi == null ? BARRING_SERVICE_INFO_UNKNOWN : bsi;
    }

    /** @hide */ public CellIdentity getCellIdentity() { return mCellIdentity; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        if (mCellIdentity != null) {
            dest.writeInt(1);
            mCellIdentity.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(mBarringServiceInfos.size());
        for (Map.Entry<Integer, BarringServiceInfo> e : mBarringServiceInfos.entrySet()) {
            dest.writeInt(e.getKey());
            e.getValue().writeToParcel(dest, flags);
        }
    }

    public static final Creator<BarringInfo> CREATOR = new Creator<BarringInfo>() {
        @Override public BarringInfo createFromParcel(Parcel source) {
            return new BarringInfo(source);
        }
        @Override public BarringInfo[] newArray(int size) { return new BarringInfo[size]; }
    };

    @Override public int hashCode() { return Objects.hash(mCellIdentity, mBarringServiceInfos); }

    @Override public boolean equals(Object rhs) {
        if (this == rhs) return true;
        if (!(rhs instanceof BarringInfo)) return false;
        BarringInfo other = (BarringInfo) rhs;
        return Objects.equals(mCellIdentity, other.mCellIdentity)
                && mBarringServiceInfos.equals(other.mBarringServiceInfos);
    }

    @Override public String toString() {
        return "BarringInfo {mBarringServiceInfos=" + mBarringServiceInfos + "}";
    }

    public static final class BarringServiceInfo implements Parcelable {

        public static final int BARRING_TYPE_NONE = 0;
        public static final int BARRING_TYPE_CONDITIONAL = 1;
        public static final int BARRING_TYPE_UNCONDITIONAL = 2;
        public static final int BARRING_TYPE_UNKNOWN = -1;

        private final int mBarringType;
        private final boolean mIsConditionallyBarred;
        private final int mConditionalBarringFactor;
        private final int mConditionalBarringTimeSeconds;

        /** @hide */
        public BarringServiceInfo(int barringType, boolean isConditionallyBarred,
                int conditionalBarringFactor, int conditionalBarringTimeSeconds) {
            mBarringType = barringType;
            mIsConditionallyBarred = isConditionallyBarred;
            mConditionalBarringFactor = conditionalBarringFactor;
            mConditionalBarringTimeSeconds = conditionalBarringTimeSeconds;
        }

        private BarringServiceInfo(Parcel p) {
            mBarringType = p.readInt();
            mIsConditionallyBarred = p.readBoolean();
            mConditionalBarringFactor = p.readInt();
            mConditionalBarringTimeSeconds = p.readInt();
        }

        public int getBarringType() { return mBarringType; }

        public boolean isConditionallyBarred() { return mIsConditionallyBarred; }

        public int getConditionalBarringFactor() { return mConditionalBarringFactor; }

        public int getConditionalBarringTimeSeconds() { return mConditionalBarringTimeSeconds; }

        /** AOSP: unconditional bars are always barred; conditional ones only if drawn. */
        public boolean isBarred() {
            return mBarringType == BARRING_TYPE_UNCONDITIONAL
                    || (mBarringType == BARRING_TYPE_CONDITIONAL && mIsConditionallyBarred);
        }

        @Override public int describeContents() { return 0; }

        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mBarringType);
            dest.writeBoolean(mIsConditionallyBarred);
            dest.writeInt(mConditionalBarringFactor);
            dest.writeInt(mConditionalBarringTimeSeconds);
        }

        public static final Creator<BarringServiceInfo> CREATOR =
                new Creator<BarringServiceInfo>() {
            @Override public BarringServiceInfo createFromParcel(Parcel source) {
                return new BarringServiceInfo(source);
            }
            @Override public BarringServiceInfo[] newArray(int size) {
                return new BarringServiceInfo[size];
            }
        };

        @Override public int hashCode() {
            return Objects.hash(mBarringType, mIsConditionallyBarred, mConditionalBarringFactor,
                    mConditionalBarringTimeSeconds);
        }

        @Override public boolean equals(Object rhs) {
            if (this == rhs) return true;
            if (!(rhs instanceof BarringServiceInfo)) return false;
            BarringServiceInfo other = (BarringServiceInfo) rhs;
            return mBarringType == other.mBarringType
                    && mIsConditionallyBarred == other.mIsConditionallyBarred
                    && mConditionalBarringFactor == other.mConditionalBarringFactor
                    && mConditionalBarringTimeSeconds == other.mConditionalBarringTimeSeconds;
        }

        @Override public String toString() {
            return "BarringServiceInfo{mBarringType=" + mBarringType + "}";
        }
    }
}
