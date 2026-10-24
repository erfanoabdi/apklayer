// android.bluetooth.le.AdvertisingSet -- the handle to a running API-26
// advertising set.
//
// NO PUBLIC CONSTRUCTOR on AOSP either: the only way to obtain one is through
// AdvertisingSetCallback.onAdvertisingSetStarted, which the stack calls after a
// set actually starts. Mac.apk never starts one -- BluetoothLeAdvertiser
// reports ADVERTISE_FAILED_FEATURE_UNSUPPORTED instead -- so no AdvertisingSet
// is ever handed out and every method below is unreachable.
//
// The type exists because AdvertisingSetCallback names it in eight signatures:
// an app that subclasses the callback cannot be DEFINED without it.
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/le/AdvertisingSet.java.
package android.bluetooth.le;

public final class AdvertisingSet {

    AdvertisingSet() {}

    public void enableAdvertising(boolean enable, int duration, int maxExtendedAdvertisingEvents) {}

    public void setAdvertisingData(AdvertiseData advertiseData) {}

    public void setScanResponseData(AdvertiseData scanResponse) {}

    public void setAdvertisingParameters(AdvertisingSetParameters parameters) {}

    public void setPeriodicAdvertisingParameters(PeriodicAdvertisingParameters parameters) {}

    public void setPeriodicAdvertisingData(AdvertiseData periodicData) {}

    public void setPeriodicAdvertisingEnabled(boolean enable) {}
}
