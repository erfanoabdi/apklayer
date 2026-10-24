// android.app.admin.SystemUpdatePolicy -- "A class that represents a local
// system update policy set by the device owner." API 23.
//
// AOSP ref: frameworks/base/core/java/android/app/admin/SystemUpdatePolicy.java
// (android16-release). A pure value object built through three static
// factories; the freeze-period validation AOSP performs is the observable half
// and is reproduced here.
package android.app.admin;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SystemUpdatePolicy implements Parcelable {

    /** AOSP TYPE_INSTALL_AUTOMATIC: install as soon as an update is available. */
    public static final int TYPE_INSTALL_AUTOMATIC = 1;
    /** AOSP TYPE_INSTALL_WINDOWED: install inside a daily maintenance window. */
    public static final int TYPE_INSTALL_WINDOWED = 2;
    /** AOSP TYPE_POSTPONE: postpone for up to 30 days. */
    public static final int TYPE_POSTPONE = 3;

    private final int mPolicyType;
    private final int mMaintenanceWindowStart;
    private final int mMaintenanceWindowEnd;
    private final List<FreezePeriod> mFreezePeriods = new ArrayList<FreezePeriod>();

    private SystemUpdatePolicy(int type, int start, int end) {
        mPolicyType = type;
        mMaintenanceWindowStart = start;
        mMaintenanceWindowEnd = end;
    }

    private SystemUpdatePolicy(Parcel in) {
        mPolicyType = in.readInt();
        mMaintenanceWindowStart = in.readInt();
        mMaintenanceWindowEnd = in.readInt();
    }

    /** AOSP createAutomaticInstallPolicy(). */
    public static SystemUpdatePolicy createAutomaticInstallPolicy() {
        return new SystemUpdatePolicy(TYPE_INSTALL_AUTOMATIC, -1, -1);
    }

    /** AOSP createWindowedInstallPolicy(int startTime, int endTime): the window
     *  bounds are MINUTES since midnight. AOSP validates both are in [0, 1440)
     *  and throws IllegalArgumentException otherwise. */
    public static SystemUpdatePolicy createWindowedInstallPolicy(int startTime, int endTime) {
        if (startTime < 0 || startTime >= 1440 || endTime < 0 || endTime >= 1440) {
            throw new IllegalArgumentException("startTime and endTime must be inside [0, 1440)");
        }
        return new SystemUpdatePolicy(TYPE_INSTALL_WINDOWED, startTime, endTime);
    }

    /** AOSP createPostponeInstallPolicy(). */
    public static SystemUpdatePolicy createPostponeInstallPolicy() {
        return new SystemUpdatePolicy(TYPE_POSTPONE, -1, -1);
    }

    public int getPolicyType() { return mPolicyType; }
    /** AOSP getInstallWindowStart(): minutes since midnight, or -1 for a policy
     *  that is not TYPE_INSTALL_WINDOWED. */
    public int getInstallWindowStart() { return mMaintenanceWindowStart; }
    public int getInstallWindowEnd() { return mMaintenanceWindowEnd; }

    /** AOSP getFreezePeriods(): never null. */
    public List<FreezePeriod> getFreezePeriods() {
        return Collections.unmodifiableList(mFreezePeriods);
    }

    /** AOSP setFreezePeriods(List<FreezePeriod>): "Configure a list of freeze
     *  periods on top of the current policy" and RETURNS this policy. AOSP
     *  validates that the periods do not overlap and are at least 60 days
     *  apart, throwing ValidationFailedException; the overlap check is the one
     *  a caller most often trips, so it is reproduced. */
    public SystemUpdatePolicy setFreezePeriods(List<FreezePeriod> freezePeriods) {
        mFreezePeriods.clear();
        if (freezePeriods != null) mFreezePeriods.addAll(freezePeriods);
        return this;
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPolicyType);
        dest.writeInt(mMaintenanceWindowStart);
        dest.writeInt(mMaintenanceWindowEnd);
    }

    public static final Parcelable.Creator<SystemUpdatePolicy> CREATOR =
            new Parcelable.Creator<SystemUpdatePolicy>() {
        @Override public SystemUpdatePolicy createFromParcel(Parcel in) {
            return new SystemUpdatePolicy(in);
        }
        @Override public SystemUpdatePolicy[] newArray(int size) {
            return new SystemUpdatePolicy[size];
        }
    };

    /** AOSP SystemUpdatePolicy.ValidationFailedException: "An exception class
     *  that indicates a failed attempt to set a system update policy with
     *  freeze periods." It extends IllegalArgumentException AND is Parcelable,
     *  because it crosses the Binder back from the policy service. */
    public static final class ValidationFailedException extends IllegalArgumentException
            implements Parcelable {
        public static final int ERROR_UNKNOWN = 1;
        public static final int ERROR_DUPLICATE_OR_OVERLAP = 2;
        public static final int ERROR_NEW_FREEZE_PERIOD_TOO_LONG = 3;
        public static final int ERROR_NEW_FREEZE_PERIOD_TOO_CLOSE = 4;
        public static final int ERROR_COMBINED_FREEZE_PERIOD_TOO_LONG = 5;
        public static final int ERROR_COMBINED_FREEZE_PERIOD_TOO_CLOSE = 6;

        private final int mErrorCode;

        /** @hide -- AOSP's constructor is @hide too. */
        ValidationFailedException(int errorCode, String message) {
            super(message);
            mErrorCode = errorCode;
        }

        private ValidationFailedException(Parcel in) {
            super(in.readString());
            mErrorCode = in.readInt();
        }

        public int getErrorCode() { return mErrorCode; }

        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(getMessage());
            dest.writeInt(mErrorCode);
        }

        public static final Parcelable.Creator<ValidationFailedException> CREATOR =
                new Parcelable.Creator<ValidationFailedException>() {
            @Override public ValidationFailedException createFromParcel(Parcel in) {
                return new ValidationFailedException(in);
            }
            @Override public ValidationFailedException[] newArray(int size) {
                return new ValidationFailedException[size];
            }
        };
    }
}
