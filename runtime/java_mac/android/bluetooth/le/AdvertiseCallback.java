// android.bluetooth.le.AdvertiseCallback -- the result callback for
// BluetoothLeAdvertiser.startAdvertising().
//
// Mirrored exactly (both methods are concrete on AOSP, so an app overrides
// only what it needs). Mac.apk always calls onStartFailure with
// ADVERTISE_FAILED_FEATURE_UNSUPPORTED -- see BluetoothLeAdvertiser for why
// that is the honest code and why failing beats going quiet.
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/le/AdvertiseCallback.java.
package android.bluetooth.le;

public abstract class AdvertiseCallback {

    public static final int ADVERTISE_FAILED_DATA_TOO_LARGE = 1;
    public static final int ADVERTISE_FAILED_TOO_MANY_ADVERTISERS = 2;
    public static final int ADVERTISE_FAILED_ALREADY_STARTED = 3;
    public static final int ADVERTISE_FAILED_INTERNAL_ERROR = 4;
    public static final int ADVERTISE_FAILED_FEATURE_UNSUPPORTED = 5;

    public AdvertiseCallback() {}

    public void onStartSuccess(AdvertiseSettings settingsInEffect) {}

    public void onStartFailure(int errorCode) {}
}
