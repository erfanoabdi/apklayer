// android.bluetooth.le.BluetoothLeAdvertiser -- starts and stops BLE
// advertisements (this device acting as a peripheral).
//
// NOTHING CAN BE BROADCAST, and the callbacks say so rather than going quiet.
// Mac.apk exposes no Bluetooth bridge, so BluetoothAdapter.getDefaultAdapter()
// returns null (AOSP's answer for a context with no Bluetooth) and
// getBluetoothLeAdvertiser() returns null with it -- which means an app cannot
// reach this class through supported code at all. The constructor is
// package-private, as on AOSP, so it cannot fabricate one either.
//
// If one is somehow held, every start* method reports failure through the
// app's OWN callback with ADVERTISE_FAILED_FEATURE_UNSUPPORTED. That is the
// honest code -- AOSP uses it for a device whose controller cannot advertise,
// which is exactly this device -- and reporting beats a silent return: an app
// that got neither onStartSuccess nor onStartFailure would wait forever, which
// is the failure shape this framework refuses everywhere (compare
// android.telecom.TelecomManager's class javadoc).
//
// The stop* methods are REMOVALS and are therefore silent no-ops: nothing is
// advertising, and firing a failure out of a teardown path would be noise.
//
// Mirrors packages/modules/Bluetooth
// framework/java/android/bluetooth/le/BluetoothLeAdvertiser.java.
package android.bluetooth.le;

import android.os.Handler;
import android.os.Looper;

public final class BluetoothLeAdvertiser {

    BluetoothLeAdvertiser() {}

    public void startAdvertising(AdvertiseSettings settings, AdvertiseData advertiseData,
            AdvertiseCallback callback) {
        startAdvertising(settings, advertiseData, null, callback);
    }

    public void startAdvertising(AdvertiseSettings settings, AdvertiseData advertiseData,
            AdvertiseData scanResponse, AdvertiseCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback cannot be null");
        }
        final AdvertiseCallback cb = callback;
        post(() -> cb.onStartFailure(AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED));
    }

    /** A removal: nothing is advertising, so there is nothing to stop. */
    public void stopAdvertising(AdvertiseCallback callback) {}

    public void startAdvertisingSet(AdvertisingSetParameters parameters,
            AdvertiseData advertiseData, AdvertiseData scanResponse,
            PeriodicAdvertisingParameters periodicParameters, AdvertiseData periodicData,
            AdvertisingSetCallback callback) {
        startAdvertisingSet(parameters, advertiseData, scanResponse, periodicParameters,
                periodicData, 0, 0, callback, null);
    }

    public void startAdvertisingSet(AdvertisingSetParameters parameters,
            AdvertiseData advertiseData, AdvertiseData scanResponse,
            PeriodicAdvertisingParameters periodicParameters, AdvertiseData periodicData,
            AdvertisingSetCallback callback, Handler handler) {
        startAdvertisingSet(parameters, advertiseData, scanResponse, periodicParameters,
                periodicData, 0, 0, callback, handler);
    }

    public void startAdvertisingSet(AdvertisingSetParameters parameters,
            AdvertiseData advertiseData, AdvertiseData scanResponse,
            PeriodicAdvertisingParameters periodicParameters, AdvertiseData periodicData,
            int duration, int maxExtendedAdvertisingEvents, AdvertisingSetCallback callback) {
        startAdvertisingSet(parameters, advertiseData, scanResponse, periodicParameters,
                periodicData, duration, maxExtendedAdvertisingEvents, callback, null);
    }

    public void startAdvertisingSet(AdvertisingSetParameters parameters,
            AdvertiseData advertiseData, AdvertiseData scanResponse,
            PeriodicAdvertisingParameters periodicParameters, AdvertiseData periodicData,
            int duration, int maxExtendedAdvertisingEvents, AdvertisingSetCallback callback,
            Handler handler) {
        if (callback == null) {
            throw new IllegalArgumentException("callback cannot be null");
        }
        final AdvertisingSetCallback cb = callback;
        // AOSP reports the failure with a NULL AdvertisingSet, because no set
        // was created -- so this hands back null rather than a hollow handle.
        Runnable fail = () -> cb.onAdvertisingSetStarted(null, 0,
                AdvertisingSetCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED);
        if (handler != null) {
            handler.post(fail);
        } else {
            post(fail);
        }
    }

    /** A removal: no set is running, so there is nothing to stop. */
    public void stopAdvertisingSet(AdvertisingSetCallback callback) {}

    /** AOSP delivers advertiser callbacks on the main looper by default. */
    private static void post(Runnable r) {
        Looper main = Looper.getMainLooper();
        if (main != null) {
            new Handler(main).post(r);
        } else {
            r.run();
        }
    }
}
