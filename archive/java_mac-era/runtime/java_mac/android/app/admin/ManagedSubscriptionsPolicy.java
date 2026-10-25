// android.app.admin.ManagedSubscriptionsPolicy -- "Class to represent the policy
// Device Policy Manager has on managed SIM subscriptions." API 33.
//
// AOSP ref: frameworks/base/core/java/android/app/admin/
// ManagedSubscriptionsPolicy.java (android16-release).
package android.app.admin;

import android.os.Parcel;
import android.os.Parcelable;

public final class ManagedSubscriptionsPolicy implements Parcelable {

    /** AOSP TYPE_ALL_PERSONAL_SUBSCRIPTIONS: "all subscriptions are placed in
     *  the personal profile" -- the DEFAULT, and therefore the value an
     *  unmanaged device reports. */
    public static final int TYPE_ALL_PERSONAL_SUBSCRIPTIONS = 0;
    /** AOSP TYPE_ALL_MANAGED_SUBSCRIPTIONS: all subscriptions are managed by
     *  the work profile. */
    public static final int TYPE_ALL_MANAGED_SUBSCRIPTIONS = 1;

    private final int mPolicyType;

    /** AOSP ManagedSubscriptionsPolicy(int policyType): AOSP validates the
     *  type. */
    public ManagedSubscriptionsPolicy(int policyType) {
        if (policyType != TYPE_ALL_PERSONAL_SUBSCRIPTIONS
                && policyType != TYPE_ALL_MANAGED_SUBSCRIPTIONS) {
            throw new IllegalArgumentException("Invalid policy type: " + policyType);
        }
        mPolicyType = policyType;
    }

    private ManagedSubscriptionsPolicy(Parcel in) { mPolicyType = in.readInt(); }

    public int getPolicyType() { return mPolicyType; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { dest.writeInt(mPolicyType); }
    @Override public String toString() {
        return "ManagedSubscriptionsPolicy (type: " + mPolicyType + ")";
    }

    public static final Parcelable.Creator<ManagedSubscriptionsPolicy> CREATOR =
            new Parcelable.Creator<ManagedSubscriptionsPolicy>() {
        @Override public ManagedSubscriptionsPolicy createFromParcel(Parcel in) {
            return new ManagedSubscriptionsPolicy(in);
        }
        @Override public ManagedSubscriptionsPolicy[] newArray(int size) {
            return new ManagedSubscriptionsPolicy[size];
        }
    };
}
