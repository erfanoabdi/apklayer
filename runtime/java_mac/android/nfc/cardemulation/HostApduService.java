// android.nfc.cardemulation.HostApduService -- the Service an app publishes to
// emulate a contactless smartcard (HCE), answering APDUs from a reader.
//
// The SERVICE half is real: it extends android.app.Service, so an app's
// subclass is a normal component and its onCreate/onDestroy run as usual.
//
// onBind() returns NULL, deliberately, exactly as android.telecom
// .ConnectionService's does. On Android it hands back the binder the NFC
// service uses to push APDUs in; there is no NFC service on a Mac, and
// returning a binder connected to nothing would tell the app card emulation
// was live. Null is also android.app.Service's default for a service that
// offers no binding, so callers already handle it.
//
// Consequently processCommandApdu() and onDeactivated() are never invoked.
// They are ABSTRACT, as on AOSP -- that is the whole reason the class must
// link, since an app's subclass declares them.
//
// Mirrors packages/modules/Nfc
// framework/java/android/nfc/cardemulation/HostApduService.java.
package android.nfc.cardemulation;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

public abstract class HostApduService extends Service {

    public static final String SERVICE_INTERFACE =
            "android.nfc.cardemulation.action.HOST_APDU_SERVICE";
    public static final String SERVICE_META_DATA =
            "android.nfc.cardemulation.host_apdu_service";

    /** AOSP: the reader deselected this AID. */
    public static final int DEACTIVATION_DESELECTED = 0;
    /** AOSP: the RF link was lost. */
    public static final int DEACTIVATION_LINK_LOSS = 1;

    public HostApduService() {}

    /** Null: nothing can bind this, so claiming otherwise would mislead. */
    @Override
    public final IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * AOSP sends the response over the NFC service binder. There is no binder
     * and no reader in the field, so the response has nowhere to go. It is a
     * no-op rather than a throw because AOSP itself drops the call when its
     * messenger is null, and because an app calls this from inside its own
     * APDU handler -- a path that, here, is already unreachable.
     */
    public final void sendResponseApdu(byte[] responseApdu) {}

    /** @see #sendResponseApdu -- same reasoning. */
    public final void notifyUnhandled() {}

    // Inbound from the NFC service. Never invoked here; abstract so an app's
    // subclass declares them, which is the point of the type.

    public abstract byte[] processCommandApdu(byte[] commandApdu, Bundle extras);

    public abstract void onDeactivated(int reason);
}
