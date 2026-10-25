// android.companion.CompanionDeviceManager -- the system service an app uses
// to associate itself with a nearby watch / glasses / computer, and then talk
// to it through the platform.
//
// WHAT A MAC DOES NOT HAVE, and why nothing here can be faked. An association
// is created by a SYSTEM PAIRING UI: the platform scans (BLE / classic
// Bluetooth / Wi-Fi), shows the user a chooser, and records the user's choice.
// Mac.apk is a single-app host with no system UI to show, no companion
// transport, and no Bluetooth bridge at all (see android.bluetooth). So NO
// association can exist, and every per-association operation below is being
// asked about a device this app is not associated with.
//
// THE ANSWERS ARE AOSP'S OWN. AOSP guards every method with
// `if (mService == null) { Log.w(TAG, "CompanionDeviceManager service is not
// available."); return <default>; }` -- a branch that exists precisely for a
// device where the companion service is absent, which is literally our case.
// So the empty lists, the falses and the nulls below are not invented
// degradations; they are the values AOSP itself returns in this exact state,
// and each one is a complete, unambiguous answer ("there are no associations",
// "this app has no notification access").
//
// ONE DELIBERATE DEVIATION: associate(). AOSP's null-service branch returns
// SILENTLY, which leaves the app waiting forever on a Callback that can never
// fire -- the failure shape this framework avoids everywhere (compare
// android.telecom.TelecomManager's class javadoc). We report the failure
// through the app's OWN Callback instead, using AOSP's documented contract
// that both onFailure overloads are invoked when an association cannot be
// created. RESULT_INTERNAL_ERROR is the honest code: the request did not time
// out and the user did not reject it -- this platform simply cannot serve it.
//
// Mirrors frameworks/base/core/java/android/companion/CompanionDeviceManager.java;
// public surface confirmed against frameworks/base/core/api/current.txt.
package android.companion;

import android.content.ComponentName;
import android.content.Context;
import android.content.IntentSender;
import android.os.Handler;
import android.os.OutcomeReceiver;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public final class CompanionDeviceManager {

    public static final String EXTRA_ASSOCIATION = "android.companion.extra.ASSOCIATION";
    /** @deprecated on AOSP in favour of EXTRA_ASSOCIATION. */
    @Deprecated
    public static final String EXTRA_DEVICE = "android.companion.extra.DEVICE";

    public static final int RESULT_OK = -1;
    public static final int RESULT_CANCELED = 0;
    public static final int RESULT_USER_REJECTED = 1;
    public static final int RESULT_DISCOVERY_TIMEOUT = 2;
    public static final int RESULT_INTERNAL_ERROR = 3;
    public static final int RESULT_SECURITY_ERROR = 4;

    /** The one system-data-sync type AOSP defines publicly. */
    public static final int FLAG_CALL_METADATA = 1;

    /** The message every failure below carries, so a log makes the cause obvious. */
    private static final String NO_COMPANION =
            "This host has no companion-device subsystem: there is no system pairing UI to "
                    + "create an association and no companion transport to carry one.";

    /**
     * The pairing-flow callbacks. onFailure(CharSequence) is ABSTRACT on AOSP
     * and so it is here -- an app's subclass always implements it, which is
     * what makes reporting the failure below safe.
     */
    public abstract static class Callback {

        public Callback() {}

        /** @deprecated on AOSP, renamed to onAssociationPending. */
        @Deprecated
        public void onDeviceFound(IntentSender chooserLauncher) {}

        /** AOSP's default delegates to the deprecated name so an app that
         *  overrode either one is called. */
        public void onAssociationPending(IntentSender intentSender) {
            onDeviceFound(intentSender);
        }

        public void onAssociationCreated(AssociationInfo associationInfo) {}

        public abstract void onFailure(CharSequence error);

        /** AOSP: "both onFailure(CharSequence) and this API will be called if
         *  the association could not be created", so this one does NOT
         *  delegate -- doing so would report the same failure twice. */
        public void onFailure(int errorCode, CharSequence error) {}
    }

    public CompanionDeviceManager() {}

    public CompanionDeviceManager(Context context) {}

    // ---- Queries. Empty/false/null are AOSP's own no-service answers. ----

    /** @deprecated on AOSP in favour of getMyAssociations(). */
    @Deprecated
    public List<String> getAssociations() { return Collections.emptyList(); }

    public List<AssociationInfo> getMyAssociations() { return Collections.emptyList(); }

    /** @deprecated on AOSP. No notification-listener plumbing exists here. */
    @Deprecated
    public boolean hasNotificationAccess(ComponentName component) { return false; }

    public boolean isPermissionTransferUserConsented(int associationId) { return false; }

    /** Nothing is bonded, so there is no bond to remove; false says so. */
    public boolean removeBond(int associationId) { return false; }

    /** There is no pending association, so there is nothing to cancel. AOSP
     *  returns null here too, and the method is @Nullable. */
    public IntentSender buildAssociationCancellationIntent() { return null; }

    /**
     * @throws DeviceNotAssociatedException always. This app is not associated
     * with any device, which is exactly what the exception means -- and AOSP
     * raises the same type for an unknown association id. Returning null
     * instead would read as "consent already handled".
     */
    public IntentSender buildPermissionTransferUserConsentIntent(int associationId) {
        throw new DeviceNotAssociatedException(NO_COMPANION);
    }

    // ---- Association lifecycle. ----

    /**
     * Reports failure through the caller's Callback rather than returning
     * silently as AOSP's no-service branch does. See the class header for why.
     */
    public void associate(AssociationRequest request, Callback callback, Handler handler) {
        java.util.Objects.requireNonNull(request, "Request cannot be null");
        java.util.Objects.requireNonNull(callback, "Callback cannot be null");
        Handler target = handler;
        if (target == null) {
            target = new Handler(android.os.Looper.getMainLooper());
        }
        final Callback cb = callback;
        target.post(() -> failAssociation(cb));
    }

    /** @see #associate(AssociationRequest, Callback, Handler) */
    public void associate(AssociationRequest request, Executor executor, Callback callback) {
        java.util.Objects.requireNonNull(request, "Request cannot be null");
        java.util.Objects.requireNonNull(executor, "Executor cannot be null");
        java.util.Objects.requireNonNull(callback, "Callback cannot be null");
        final Callback cb = callback;
        executor.execute(() -> failAssociation(cb));
    }

    private static void failAssociation(Callback callback) {
        // AOSP invokes BOTH overloads on failure; an app may have overridden
        // only the coded one, so calling just the abstract one would lose it.
        callback.onFailure(NO_COMPANION);
        callback.onFailure(RESULT_INTERNAL_ERROR, NO_COMPANION);
    }

    /**
     * A REMOVAL, so a no-op cannot create a false expectation -- there is
     * nothing associated to remove, and AOSP's own no-service branch also
     * returns without acting.
     *
     * @deprecated on AOSP in favour of disassociate(int).
     */
    @Deprecated
    public void disassociate(String deviceMacAddress) {}

    /** @see #disassociate(String) */
    public void disassociate(int associationId) {}

    /**
     * On AOSP this launches a system settings screen. There is no such screen
     * here, and no callback is pending on it: an app checks
     * hasNotificationAccess() afterwards, which keeps answering false --
     * truthfully. AOSP's own no-service branch also returns without acting.
     */
    public void requestNotificationAccess(ComponentName component) {}

    // ---- Per-association operations. No association exists. ----

    /** Tagging an association that does not exist has no effect. AOSP's
     *  no-service branch likewise returns without acting, and the tag is only
     *  ever read back through getMyAssociations(), which stays empty -- so the
     *  app can still see the truth. */
    public void setDeviceId(int associationId, DeviceId deviceId) {}

    public void enableSystemDataSyncForTypes(int associationId, int flags) {}

    public void disableSystemDataSyncForTypes(int associationId, int flags) {}

    /**
     * @throws DeviceNotAssociatedException always. Attaching a transport is
     * the app handing over its own streams and then WAITING for the platform
     * to speak the companion protocol over them; AOSP's silent no-service
     * return would strand those streams open forever, so this refuses instead.
     */
    public void attachSystemDataTransport(int associationId, InputStream in, OutputStream out)
            throws DeviceNotAssociatedException {
        throw new DeviceNotAssociatedException(NO_COMPANION);
    }

    /**
     * A REMOVAL, and one that runs on teardown paths, so it is a no-op rather
     * than a throw -- the same call this framework makes for
     * TelecomManager.unregisterPhoneAccount. Nothing was ever attached
     * (attachSystemDataTransport throws), so detaching nothing is honest.
     */
    public void detachSystemDataTransport(int associationId) {}

    /**
     * @throws DeviceNotAssociatedException always, synchronously. The caller
     * holds an OutcomeReceiver and would otherwise wait forever; AOSP declares
     * this exception on exactly this method, so it is the documented shape.
     */
    public void startSystemDataTransfer(int associationId, Executor executor,
            OutcomeReceiver<Void, CompanionException> callback)
            throws DeviceNotAssociatedException {
        throw new DeviceNotAssociatedException(NO_COMPANION);
    }

    /**
     * @throws DeviceNotAssociatedException always: presence can only be
     * observed for a device this app is associated with, and it is associated
     * with none. AOSP declares the same exception on this overload.
     *
     * @deprecated on AOSP in favour of the ObservingDevicePresenceRequest form.
     */
    @Deprecated
    public void startObservingDevicePresence(String deviceAddress)
            throws DeviceNotAssociatedException {
        throw new DeviceNotAssociatedException(NO_COMPANION);
    }

    /**
     * The newer overload declares NO exception, so it cannot report the
     * failure that way. It is a no-op, matching AOSP's own no-service branch:
     * no presence event is ever delivered, and the app's
     * CompanionDeviceService simply never hears from us -- which is the
     * truthful outcome for a host with nothing to observe.
     */
    public void startObservingDevicePresence(ObservingDevicePresenceRequest request) {
        java.util.Objects.requireNonNull(request, "request cannot be null");
    }

    /**
     * A removal: nothing is being observed, so stopping is genuinely a no-op.
     *
     * @deprecated on AOSP in favour of the ObservingDevicePresenceRequest form.
     */
    @Deprecated
    public void stopObservingDevicePresence(String deviceAddress)
            throws DeviceNotAssociatedException {
    }

    /** @see #stopObservingDevicePresence(String) */
    public void stopObservingDevicePresence(ObservingDevicePresenceRequest request) {
        java.util.Objects.requireNonNull(request, "request cannot be null");
    }
}
