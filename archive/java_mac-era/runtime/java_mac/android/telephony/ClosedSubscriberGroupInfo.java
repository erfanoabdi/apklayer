package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Closed Subscriber Group information carried by an LTE/WCDMA cell identity.
 *
 * Immutable value type; no radio needed. Mac.apk never produces a cell identity,
 * so getClosedSubscriberGroupInfo() reports null (AOSP's answer when the cell
 * does not advertise a CSG).
 *
 * Source: frameworks/base/telephony/java/android/telephony/ClosedSubscriberGroupInfo.java
 */
public final class ClosedSubscriberGroupInfo implements Parcelable {

    private final boolean mCsgIndicator;
    private final String mHomeNodebName;
    private final int mCsgIdentity;

    /** @hide */
    public ClosedSubscriberGroupInfo(boolean csgIndicator, String homeNodebName, int csgIdentity) {
        mCsgIndicator = csgIndicator;
        mHomeNodebName = homeNodebName == null ? "" : homeNodebName;
        mCsgIdentity = csgIdentity;
    }

    public boolean getCsgIndicator() { return mCsgIndicator; }

    public String getHomeNodebName() { return mHomeNodebName; }

    public int getCsgIdentity() { return mCsgIdentity; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClosedSubscriberGroupInfo)) return false;
        ClosedSubscriberGroupInfo other = (ClosedSubscriberGroupInfo) o;
        return mCsgIndicator == other.mCsgIndicator
                && mHomeNodebName.equals(other.mHomeNodebName)
                && mCsgIdentity == other.mCsgIdentity;
    }

    @Override public int hashCode() {
        return Objects.hash(mCsgIndicator, mHomeNodebName, mCsgIdentity);
    }

    @Override public String toString() {
        return "ClosedSubscriberGroupInfo:{ mCsgIndicator=" + mCsgIndicator
                + " mHomeNodebName=" + mHomeNodebName + " mCsgIdentity=" + mCsgIdentity + "}";
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int type) {
        dest.writeBoolean(mCsgIndicator);
        dest.writeString(mHomeNodebName);
        dest.writeInt(mCsgIdentity);
    }

    /** @hide */
    public static ClosedSubscriberGroupInfo createFromParcel(Parcel in) {
        return new ClosedSubscriberGroupInfo(in.readBoolean(), in.readString(), in.readInt());
    }

    public static final Creator<ClosedSubscriberGroupInfo> CREATOR =
            new Creator<ClosedSubscriberGroupInfo>() {
        @Override public ClosedSubscriberGroupInfo createFromParcel(Parcel in) {
            return ClosedSubscriberGroupInfo.createFromParcel(in);
        }
        @Override public ClosedSubscriberGroupInfo[] newArray(int size) {
            return new ClosedSubscriberGroupInfo[size];
        }
    };
}
