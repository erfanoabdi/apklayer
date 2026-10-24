// android.companion.AssociationRequest -- what an app asks for when it calls
// CompanionDeviceManager.associate(): which device profile, which filters,
// single-device or a chooser.
//
// A REAL, complete value type built by its own Builder. Every accessor returns
// what the app put in, with no device dependency, so building and inspecting a
// request behaves the same as on Android. What is absent is the system pairing
// UI that would SERVICE the request -- see CompanionDeviceManager.associate.
//
// Mirrors frameworks/base/core/java/android/companion/AssociationRequest.java;
// public surface and profile constants confirmed against
// frameworks/base/core/api/current.txt.
package android.companion;

import android.graphics.drawable.Icon;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AssociationRequest implements Parcelable {

    public static final String DEVICE_PROFILE_WATCH =
            "android.app.role.COMPANION_DEVICE_WATCH";
    public static final String DEVICE_PROFILE_COMPUTER =
            "android.app.role.COMPANION_DEVICE_COMPUTER";
    public static final String DEVICE_PROFILE_APP_STREAMING =
            "android.app.role.COMPANION_DEVICE_APP_STREAMING";
    public static final String DEVICE_PROFILE_AUTOMOTIVE_PROJECTION =
            "android.app.role.SYSTEM_AUTOMOTIVE_PROJECTION";
    public static final String DEVICE_PROFILE_NEARBY_DEVICE_STREAMING =
            "android.app.role.COMPANION_DEVICE_NEARBY_DEVICE_STREAMING";
    public static final String DEVICE_PROFILE_GLASSES =
            "android.app.role.COMPANION_DEVICE_GLASSES";
    public static final String DEVICE_PROFILE_VIRTUAL_DEVICE =
            "android.app.role.COMPANION_DEVICE_VIRTUAL_DEVICE";

    private final boolean mSingleDevice;
    private final List<DeviceFilter<?>> mDeviceFilters;
    private final String mDeviceProfile;
    private final CharSequence mDisplayName;
    private final boolean mSelfManaged;
    private final boolean mForceConfirmation;
    private final Icon mDeviceIcon;

    private AssociationRequest(Builder b) {
        mSingleDevice = b.mSingleDevice;
        mDeviceFilters = Collections.unmodifiableList(new ArrayList<>(b.mDeviceFilters));
        mDeviceProfile = b.mDeviceProfile;
        mDisplayName = b.mDisplayName;
        mSelfManaged = b.mSelfManaged;
        mForceConfirmation = b.mForceConfirmation;
        mDeviceIcon = b.mDeviceIcon;
    }

    public boolean isSingleDevice() { return mSingleDevice; }

    public String getDeviceProfile() { return mDeviceProfile; }

    public CharSequence getDisplayName() { return mDisplayName; }

    public boolean isSelfManaged() { return mSelfManaged; }

    public boolean isForceConfirmation() { return mForceConfirmation; }

    public Icon getDeviceIcon() { return mDeviceIcon; }

    /** @hide on AOSP, but the filters have to be readable by the manager. */
    public List<DeviceFilter<?>> getDeviceFilters() { return mDeviceFilters; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeBoolean(mSingleDevice);
        dest.writeString(mDeviceProfile);
        dest.writeCharSequence(mDisplayName);
        dest.writeBoolean(mSelfManaged);
        dest.writeBoolean(mForceConfirmation);
        // Filters are not marshalled: they are an open set of Parcelable
        // implementations and nothing in this tree consumes a marshalled
        // request (there is no companion service to send it to).
    }

    public static final Creator<AssociationRequest> CREATOR = new Creator<AssociationRequest>() {
        @Override public AssociationRequest createFromParcel(Parcel source) {
            Builder b = new Builder();
            b.setSingleDevice(source.readBoolean());
            String profile = source.readString();
            if (profile != null) {
                b.setDeviceProfile(profile);
            }
            CharSequence displayName = source.readCharSequence();
            if (displayName != null) {
                b.setDisplayName(displayName);
            }
            b.setSelfManaged(source.readBoolean());
            b.setForceConfirmation(source.readBoolean());
            return b.build();
        }

        @Override public AssociationRequest[] newArray(int size) {
            return new AssociationRequest[size];
        }
    };

    public static final class Builder {
        private boolean mSingleDevice = false;
        private final List<DeviceFilter<?>> mDeviceFilters = new ArrayList<>();
        private String mDeviceProfile;
        private CharSequence mDisplayName;
        private boolean mSelfManaged = false;
        private boolean mForceConfirmation = false;
        private Icon mDeviceIcon;

        public Builder() {}

        public Builder setSingleDevice(boolean singleDevice) {
            mSingleDevice = singleDevice;
            return this;
        }

        /** AOSP ignores a null filter rather than storing it. */
        public Builder addDeviceFilter(DeviceFilter<?> deviceFilter) {
            if (deviceFilter != null) {
                mDeviceFilters.add(deviceFilter);
            }
            return this;
        }

        public Builder setDeviceProfile(String deviceProfile) {
            mDeviceProfile = deviceProfile;
            return this;
        }

        public Builder setDisplayName(CharSequence displayName) {
            mDisplayName = displayName;
            return this;
        }

        public Builder setSelfManaged(boolean selfManaged) {
            mSelfManaged = selfManaged;
            return this;
        }

        public Builder setForceConfirmation(boolean forceConfirmation) {
            mForceConfirmation = forceConfirmation;
            return this;
        }

        public Builder setDeviceIcon(Icon deviceIcon) {
            mDeviceIcon = deviceIcon;
            return this;
        }

        /** AOSP requires a display name for a self-managed association. */
        public AssociationRequest build() {
            if (mSelfManaged && mDisplayName == null) {
                throw new IllegalStateException("Request for a self-managed association MUST "
                        + "provide the display name of the device");
            }
            return new AssociationRequest(this);
        }
    }
}
