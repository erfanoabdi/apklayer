// android.companion.AssociationInfo -- the record of one completed
// companion-device association.
//
// NO PUBLIC CONSTRUCTOR on AOSP either: the system creates one when the user
// confirms a device in the companion pairing UI. Mac.apk has no such UI and no
// companion transport, so no association is ever created and
// CompanionDeviceManager.getMyAssociations() is empty -- see that class. The
// type is here so an app that names it (notably in its own Callback override)
// can link.
//
// Mirrors frameworks/base/core/java/android/companion/AssociationInfo.java;
// public surface confirmed against frameworks/base/core/api/current.txt.
package android.companion;

import android.graphics.drawable.Icon;
import android.net.MacAddress;
import android.os.Parcel;
import android.os.Parcelable;

public final class AssociationInfo implements Parcelable {

    private final int mId;
    private final MacAddress mDeviceMacAddress;
    private final CharSequence mDisplayName;
    private final String mDeviceProfile;
    private final AssociatedDevice mAssociatedDevice;
    private final boolean mSelfManaged;
    private final int mSystemDataSyncFlags;
    private final DeviceId mDeviceId;
    private final Icon mDeviceIcon;

    AssociationInfo(int id, MacAddress deviceMacAddress, CharSequence displayName,
            String deviceProfile, AssociatedDevice associatedDevice, boolean selfManaged,
            int systemDataSyncFlags, DeviceId deviceId, Icon deviceIcon) {
        mId = id;
        mDeviceMacAddress = deviceMacAddress;
        mDisplayName = displayName;
        mDeviceProfile = deviceProfile;
        mAssociatedDevice = associatedDevice;
        mSelfManaged = selfManaged;
        mSystemDataSyncFlags = systemDataSyncFlags;
        mDeviceId = deviceId;
        mDeviceIcon = deviceIcon;
    }

    public int getId() { return mId; }

    public MacAddress getDeviceMacAddress() { return mDeviceMacAddress; }

    public CharSequence getDisplayName() { return mDisplayName; }

    public String getDeviceProfile() { return mDeviceProfile; }

    public AssociatedDevice getAssociatedDevice() { return mAssociatedDevice; }

    public boolean isSelfManaged() { return mSelfManaged; }

    public int getSystemDataSyncFlags() { return mSystemDataSyncFlags; }

    public DeviceId getDeviceId() { return mDeviceId; }

    public Icon getDeviceIcon() { return mDeviceIcon; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mId);
        dest.writeString(mDeviceMacAddress == null ? null : mDeviceMacAddress.toString());
        dest.writeCharSequence(mDisplayName);
        dest.writeString(mDeviceProfile);
        dest.writeBoolean(mSelfManaged);
        dest.writeInt(mSystemDataSyncFlags);
    }

    public static final Creator<AssociationInfo> CREATOR = new Creator<AssociationInfo>() {
        @Override public AssociationInfo createFromParcel(Parcel source) {
            int id = source.readInt();
            String mac = source.readString();
            CharSequence displayName = source.readCharSequence();
            String deviceProfile = source.readString();
            boolean selfManaged = source.readBoolean();
            int syncFlags = source.readInt();
            return new AssociationInfo(id,
                    mac == null ? null : MacAddress.fromString(mac),
                    displayName, deviceProfile, null, selfManaged, syncFlags, null, null);
        }

        @Override public AssociationInfo[] newArray(int size) {
            return new AssociationInfo[size];
        }
    };
}
