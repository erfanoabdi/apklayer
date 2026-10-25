// android.bluetooth.BluetoothHealthAppConfiguration -- the registration record
// for the (long deprecated) Health Device Profile.
//
// @deprecated on AOSP since API 29. NO PUBLIC CONSTRUCTOR there either: only
// BluetoothHealth.registerSinkAppConfiguration() produces one, and that returns
// false here without registering anything (there is no Bluetooth bridge), so
// no configuration is ever created. The type exists so BluetoothHealth's and
// BluetoothHealthCallback's signatures resolve.
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/BluetoothHealthAppConfiguration.java.
package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;

@Deprecated
public final class BluetoothHealthAppConfiguration implements Parcelable {

    private final String mName;
    private final int mDataType;
    private final int mRole;

    BluetoothHealthAppConfiguration(String name, int dataType, int role) {
        mName = name;
        mDataType = dataType;
        mRole = role;
    }

    public String getName() { return mName; }

    public int getDataType() { return mDataType; }

    public int getRole() { return mRole; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeString(mName);
        out.writeInt(mDataType);
        out.writeInt(mRole);
    }

    public static final Creator<BluetoothHealthAppConfiguration> CREATOR =
            new Creator<BluetoothHealthAppConfiguration>() {
        @Override public BluetoothHealthAppConfiguration createFromParcel(Parcel in) {
            return new BluetoothHealthAppConfiguration(in.readString(), in.readInt(),
                    in.readInt());
        }

        @Override public BluetoothHealthAppConfiguration[] newArray(int size) {
            return new BluetoothHealthAppConfiguration[size];
        }
    };
}
