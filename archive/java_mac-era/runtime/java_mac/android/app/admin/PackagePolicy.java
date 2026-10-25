// android.app.admin.PackagePolicy -- "Class representing the package policy of a
// device", an allow/deny list of package names attached to a specific policy
// area (contacts access, credential manager, ...). API 34.
//
// AOSP ref: frameworks/base/core/java/android/app/admin/PackagePolicy.java
// (android16-release). A pure value object; nothing about it needs a device
// owner to construct, which is why the constructors work here even though
// DevicePolicyManager refuses to APPLY one (see its class comment).
package android.app.admin;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class PackagePolicy implements Parcelable {

    /** AOSP PACKAGE_POLICY_BLOCKLIST: "Indicates that the policy allows all
     *  packages except those in the package set." */
    public static final int PACKAGE_POLICY_BLOCKLIST = 1;
    /** AOSP PACKAGE_POLICY_ALLOWLIST_AND_SYSTEM: only the listed packages plus
     *  system packages. */
    public static final int PACKAGE_POLICY_ALLOWLIST_AND_SYSTEM = 2;
    /** AOSP PACKAGE_POLICY_ALLOWLIST: only the listed packages. */
    public static final int PACKAGE_POLICY_ALLOWLIST = 3;

    private final int mPolicyType;
    private final Set<String> mPackageNames;

    /** AOSP PackagePolicy(int policyType): an empty package set. */
    public PackagePolicy(int policyType) {
        this(policyType, Collections.<String>emptySet());
    }

    /** AOSP PackagePolicy(int policyType, Set<String> packageNames). AOSP
     *  validates the policy type. */
    public PackagePolicy(int policyType, Set<String> packageNames) {
        if (policyType != PACKAGE_POLICY_BLOCKLIST
                && policyType != PACKAGE_POLICY_ALLOWLIST_AND_SYSTEM
                && policyType != PACKAGE_POLICY_ALLOWLIST) {
            throw new IllegalArgumentException("Invalid policy type: " + policyType);
        }
        if (packageNames == null) throw new NullPointerException("packageNames cannot be null");
        mPolicyType = policyType;
        mPackageNames = new HashSet<String>(packageNames);
    }

    private PackagePolicy(Parcel in) {
        mPolicyType = in.readInt();
        mPackageNames = new HashSet<String>();
        java.util.ArrayList<String> names = new java.util.ArrayList<String>();
        in.readStringList(names);
        mPackageNames.addAll(names);
    }

    public int getPolicyType() { return mPolicyType; }
    /** AOSP getPackageNames(): never null. */
    public Set<String> getPackageNames() { return Collections.unmodifiableSet(mPackageNames); }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPolicyType);
        dest.writeStringList(new java.util.ArrayList<String>(mPackageNames));
    }
    @Override public boolean equals(Object o) {
        if (!(o instanceof PackagePolicy)) return false;
        PackagePolicy other = (PackagePolicy) o;
        return mPolicyType == other.mPolicyType && mPackageNames.equals(other.mPackageNames);
    }
    @Override public int hashCode() { return java.util.Objects.hash(mPolicyType, mPackageNames); }

    public static final Parcelable.Creator<PackagePolicy> CREATOR =
            new Parcelable.Creator<PackagePolicy>() {
        @Override public PackagePolicy createFromParcel(Parcel in) { return new PackagePolicy(in); }
        @Override public PackagePolicy[] newArray(int size) { return new PackagePolicy[size]; }
    };
}
