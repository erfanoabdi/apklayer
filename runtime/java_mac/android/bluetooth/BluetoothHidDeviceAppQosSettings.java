// android.bluetooth.BluetoothHidDeviceAppQosSettings -- the L2CAP QoS
// parameters an app registers with the HID Device profile.
//
// A REAL, complete immutable value type with a PUBLIC constructor (as on
// AOSP), so an app that builds one and reads it back behaves exactly as on
// Android. What is absent is the profile that would apply it -- see
// BluetoothHidDevice.
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/BluetoothHidDeviceAppQosSettings.java.
package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;

public final class BluetoothHidDeviceAppQosSettings implements Parcelable {

    public static final int SERVICE_NO_TRAFFIC = 0x00;
    public static final int SERVICE_BEST_EFFORT = 0x01;
    public static final int SERVICE_GUARANTEED = 0x02;

    /** AOSP's "no limit" value for the rate/size/bandwidth fields. */
    public static final int MAX = (int) 0xffffffff;

    private final int mServiceType;
    private final int mTokenRate;
    private final int mTokenBucketSize;
    private final int mPeakBandwidth;
    private final int mLatency;
    private final int mDelayVariation;

    public BluetoothHidDeviceAppQosSettings(int serviceType, int tokenRate,
            int tokenBucketSize, int peakBandwidth, int latency, int delayVariation) {
        mServiceType = serviceType;
        mTokenRate = tokenRate;
        mTokenBucketSize = tokenBucketSize;
        mPeakBandwidth = peakBandwidth;
        mLatency = latency;
        mDelayVariation = delayVariation;
    }

    public int getServiceType() { return mServiceType; }

    public int getTokenRate() { return mTokenRate; }

    public int getTokenBucketSize() { return mTokenBucketSize; }

    public int getPeakBandwidth() { return mPeakBandwidth; }

    public int getLatency() { return mLatency; }

    public int getDelayVariation() { return mDelayVariation; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mServiceType);
        out.writeInt(mTokenRate);
        out.writeInt(mTokenBucketSize);
        out.writeInt(mPeakBandwidth);
        out.writeInt(mLatency);
        out.writeInt(mDelayVariation);
    }

    public static final Creator<BluetoothHidDeviceAppQosSettings> CREATOR =
            new Creator<BluetoothHidDeviceAppQosSettings>() {
        @Override public BluetoothHidDeviceAppQosSettings createFromParcel(Parcel in) {
            return new BluetoothHidDeviceAppQosSettings(in.readInt(), in.readInt(),
                    in.readInt(), in.readInt(), in.readInt(), in.readInt());
        }

        @Override public BluetoothHidDeviceAppQosSettings[] newArray(int size) {
            return new BluetoothHidDeviceAppQosSettings[size];
        }
    };
}
