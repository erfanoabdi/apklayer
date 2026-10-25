// android.companion.DeviceNotAssociatedException -- thrown when an app asks
// CompanionDeviceManager to act on a device it is not associated with.
//
// Real, and the one this tree throws constantly: no association can EXIST on a
// Mac (there is no system pairing UI to create one), so every per-association
// operation is being asked about a device the app is not associated with.
// That is precisely what this exception means on Android, which is why it is
// the honest answer rather than an invented error.
//
// Mirrors
// frameworks/base/core/java/android/companion/DeviceNotAssociatedException.java.
package android.companion;

public class DeviceNotAssociatedException extends RuntimeException {

    public DeviceNotAssociatedException(String message) {
        super(message);
    }
}
