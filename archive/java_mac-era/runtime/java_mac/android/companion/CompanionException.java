// android.companion.CompanionException -- the error type reported through the
// OutcomeReceiver of CompanionDeviceManager.startSystemDataTransfer().
//
// Real (a plain RuntimeException) and LOAD-BEARING: it is the value Mac.apk
// hands back to say there is no companion-device subsystem, so an app takes
// its failure path instead of waiting for a transfer that cannot happen.
//
// Mirrors frameworks/base/core/java/android/companion/CompanionException.java.
package android.companion;

public class CompanionException extends RuntimeException {

    public CompanionException(String message) {
        super(message);
    }
}
