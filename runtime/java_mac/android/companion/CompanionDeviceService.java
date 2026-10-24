// android.companion.CompanionDeviceService -- the Service the system binds to
// tell an app that one of its associated companion devices appeared or
// disappeared.
//
// The SERVICE half is real: it extends android.app.Service, so an app's
// subclass is a normal component and its onCreate/onDestroy run as usual.
//
// onBind() returns NULL, deliberately, for the same reason
// android.telecom.ConnectionService's does: on Android it hands back the
// binder the system uses to drive the service, and here there is nothing to
// drive it. Handing back a binder connected to nothing would tell the app the
// platform had attached and would then observe device presence, which it
// cannot -- there is no companion transport and no association can exist (see
// CompanionDeviceManager). Null is also android.app.Service's own default for
// a service that offers no binding, so callers already handle it.
//
// Consequently none of the onDevice* callbacks ever fires. They exist to be
// OVERRIDDEN, which is the whole reason the class must link.
//
// Mirrors
// frameworks/base/core/java/android/companion/CompanionDeviceService.java;
// public surface confirmed against frameworks/base/core/api/current.txt.
package android.companion;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.io.InputStream;
import java.io.OutputStream;

public abstract class CompanionDeviceService extends Service {

    public static final String SERVICE_INTERFACE = "android.companion.CompanionDeviceService";

    public CompanionDeviceService() {}

    /** Null: nothing can bind this, so claiming otherwise would mislead. */
    @Override
    public final IBinder onBind(Intent intent) {
        return null;
    }

    /** @see CompanionDeviceManager#attachSystemDataTransport -- same refusal,
     *  same reason: the app's streams must not be left open for a protocol
     *  that will never run. */
    public final void attachSystemDataTransport(int associationId, InputStream in,
            OutputStream out) throws DeviceNotAssociatedException {
        throw new DeviceNotAssociatedException("This host has no companion-device subsystem: "
                + "there is no system pairing UI to create an association and no companion "
                + "transport to carry one.");
    }

    /** @see CompanionDeviceManager#detachSystemDataTransport -- a removal, so
     *  a no-op is honest and safe on a teardown path. */
    public final void detachSystemDataTransport(int associationId)
            throws DeviceNotAssociatedException {
    }

    // Inbound from the system. Never invoked here -- see the header. Present
    // so an app's overrides compile and link.

    /** @deprecated on AOSP in favour of onDevicePresenceEvent. */
    @Deprecated
    public void onDeviceAppeared(String address) {}

    /** @deprecated on AOSP in favour of onDevicePresenceEvent. */
    @Deprecated
    public void onDeviceAppeared(AssociationInfo associationInfo) {}

    /** @deprecated on AOSP in favour of onDevicePresenceEvent. */
    @Deprecated
    public void onDeviceDisappeared(String address) {}

    /** @deprecated on AOSP in favour of onDevicePresenceEvent. */
    @Deprecated
    public void onDeviceDisappeared(AssociationInfo associationInfo) {}

    public void onDevicePresenceEvent(DevicePresenceEvent event) {}
}
