package android.telephony.data;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One UE Route Selection Policy rule.
 *
 * Immutable value type; Mac.apk never produces one.
 *
 * Source: frameworks/base/telephony/java/android/telephony/data/UrspRule.java
 */
public final class UrspRule implements Parcelable {

    private final int mPrecedence;
    private final List<TrafficDescriptor> mTrafficDescriptors;
    private final List<RouteSelectionDescriptor> mRouteSelectionDescriptor;

    /** @hide */
    public UrspRule(int precedence, List<TrafficDescriptor> trafficDescriptors,
            List<RouteSelectionDescriptor> routeSelectionDescriptor) {
        mPrecedence = precedence;
        mTrafficDescriptors = trafficDescriptors == null
                ? Collections.<TrafficDescriptor>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(trafficDescriptors));
        mRouteSelectionDescriptor = routeSelectionDescriptor == null
                ? Collections.<RouteSelectionDescriptor>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(routeSelectionDescriptor));
    }

    private UrspRule(Parcel p) {
        mPrecedence = p.readInt();
        List<TrafficDescriptor> tds = p.createTypedArrayList(TrafficDescriptor.CREATOR);
        mTrafficDescriptors = tds == null
                ? Collections.<TrafficDescriptor>emptyList() : Collections.unmodifiableList(tds);
        List<RouteSelectionDescriptor> rsds =
                p.createTypedArrayList(RouteSelectionDescriptor.CREATOR);
        mRouteSelectionDescriptor = rsds == null
                ? Collections.<RouteSelectionDescriptor>emptyList()
                : Collections.unmodifiableList(rsds);
    }

    public int getPrecedence() { return mPrecedence; }

    public List<TrafficDescriptor> getTrafficDescriptors() { return mTrafficDescriptors; }

    public List<RouteSelectionDescriptor> getRouteSelectionDescriptor() {
        return mRouteSelectionDescriptor;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPrecedence);
        dest.writeTypedList(mTrafficDescriptors);
        dest.writeTypedList(mRouteSelectionDescriptor);
    }

    public static final Creator<UrspRule> CREATOR = new Creator<UrspRule>() {
        @Override public UrspRule createFromParcel(Parcel in) { return new UrspRule(in); }
        @Override public UrspRule[] newArray(int size) { return new UrspRule[size]; }
    };

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UrspRule)) return false;
        UrspRule that = (UrspRule) o;
        return mPrecedence == that.mPrecedence
                && mTrafficDescriptors.equals(that.mTrafficDescriptors)
                && mRouteSelectionDescriptor.equals(that.mRouteSelectionDescriptor);
    }

    @Override public int hashCode() {
        return Objects.hash(mPrecedence, mTrafficDescriptors, mRouteSelectionDescriptor);
    }

    @Override public String toString() {
        return "UrspRule {mPrecedence=" + mPrecedence + "}";
    }
}
