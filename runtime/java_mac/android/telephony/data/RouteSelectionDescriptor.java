package android.telephony.data;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One URSP route-selection descriptor.
 *
 * Immutable value type; Mac.apk never produces one.
 *
 * Source: frameworks/base/telephony/java/android/telephony/data/RouteSelectionDescriptor.java
 */
public final class RouteSelectionDescriptor implements Parcelable {

    public static final int SESSION_TYPE_IPV4 = 0;
    public static final int SESSION_TYPE_IPV6 = 1;
    public static final int SESSION_TYPE_IPV4V6 = 2;

    public static final int ROUTE_SSC_MODE_1 = 1;
    public static final int ROUTE_SSC_MODE_2 = 2;
    public static final int ROUTE_SSC_MODE_3 = 3;

    private final int mPrecedence;
    private final int mSessionType;
    private final int mSscMode;
    private final List<NetworkSliceInfo> mSliceInfo;
    private final List<String> mDnn;

    /** @hide */
    public RouteSelectionDescriptor(int precedence, int sessionType, int sscMode,
            List<NetworkSliceInfo> sliceInfo, List<String> dnn) {
        mPrecedence = precedence;
        mSessionType = sessionType;
        mSscMode = sscMode;
        mSliceInfo = sliceInfo == null
                ? Collections.<NetworkSliceInfo>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(sliceInfo));
        mDnn = dnn == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(dnn));
    }

    private RouteSelectionDescriptor(Parcel p) {
        mPrecedence = p.readInt();
        mSessionType = p.readInt();
        mSscMode = p.readInt();
        List<NetworkSliceInfo> slices = p.createTypedArrayList(NetworkSliceInfo.CREATOR);
        mSliceInfo = slices == null
                ? Collections.<NetworkSliceInfo>emptyList() : Collections.unmodifiableList(slices);
        List<String> dnn = p.createStringArrayList();
        mDnn = dnn == null ? Collections.<String>emptyList() : Collections.unmodifiableList(dnn);
    }

    public int getPrecedence() { return mPrecedence; }
    public int getSessionType() { return mSessionType; }
    public int getSscMode() { return mSscMode; }
    public List<NetworkSliceInfo> getSliceInfo() { return mSliceInfo; }
    public List<String> getDataNetworkName() { return mDnn; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPrecedence);
        dest.writeInt(mSessionType);
        dest.writeInt(mSscMode);
        dest.writeTypedList(mSliceInfo);
        dest.writeStringList(mDnn);
    }

    public static final Creator<RouteSelectionDescriptor> CREATOR =
            new Creator<RouteSelectionDescriptor>() {
        @Override public RouteSelectionDescriptor createFromParcel(Parcel in) {
            return new RouteSelectionDescriptor(in);
        }
        @Override public RouteSelectionDescriptor[] newArray(int size) {
            return new RouteSelectionDescriptor[size];
        }
    };

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RouteSelectionDescriptor)) return false;
        RouteSelectionDescriptor that = (RouteSelectionDescriptor) o;
        return mPrecedence == that.mPrecedence && mSessionType == that.mSessionType
                && mSscMode == that.mSscMode && mSliceInfo.equals(that.mSliceInfo)
                && mDnn.equals(that.mDnn);
    }

    @Override public int hashCode() {
        return Objects.hash(mPrecedence, mSessionType, mSscMode, mSliceInfo, mDnn);
    }

    @Override public String toString() {
        return "RouteSelectionDescriptor {mPrecedence=" + mPrecedence
                + ", mSessionType=" + mSessionType + ", mSscMode=" + mSscMode + "}";
    }
}
