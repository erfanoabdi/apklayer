// android.bluetooth.BluetoothHidDeviceAppSdpSettings -- the SDP record (name,
// description, provider, subclass, HID report descriptor) an app registers
// with the HID Device profile.
//
// A REAL, complete immutable value type with a PUBLIC constructor (as on
// AOSP). What is absent is the profile that would publish it -- see
// BluetoothHidDevice.
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/BluetoothHidDeviceAppSdpSettings.java.
package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;

public final class BluetoothHidDeviceAppSdpSettings implements Parcelable {

    private final String mName;
    private final String mDescription;
    private final String mProvider;
    private final byte mSubclass;
    private final byte[] mDescriptors;

    public BluetoothHidDeviceAppSdpSettings(String name, String description, String provider,
            byte subclass, byte[] descriptors) {
        mName = name;
        mDescription = description;
        mProvider = provider;
        mSubclass = subclass;
        mDescriptors = descriptors == null ? null : descriptors.clone();
    }

    public String getName() { return mName; }

    public String getDescription() { return mDescription; }

    public String getProvider() { return mProvider; }

    public byte getSubclass() { return mSubclass; }

    public byte[] getDescriptors() { return mDescriptors; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(mName);
        out.writeString(mDescription);
        out.writeString(mProvider);
        out.writeByte(mSubclass);
        out.writeByteArray(mDescriptors);
    }

    public static final Creator<BluetoothHidDeviceAppSdpSettings> CREATOR =
            new Creator<BluetoothHidDeviceAppSdpSettings>() {
        @Override public BluetoothHidDeviceAppSdpSettings createFromParcel(Parcel in) {
            return new BluetoothHidDeviceAppSdpSettings(in.readString(), in.readString(),
                    in.readString(), in.readByte(), in.createByteArray());
        }

        @Override public BluetoothHidDeviceAppSdpSettings[] newArray(int size) {
            return new BluetoothHidDeviceAppSdpSettings[size];
        }
    };
}
