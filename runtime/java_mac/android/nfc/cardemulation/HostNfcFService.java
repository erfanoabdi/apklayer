// android.nfc.cardemulation.HostNfcFService -- the NFC-F (FeliCa) twin of
// HostApduService.
//
// Identical standing and identical reasoning: read HostApduService's header.
// The service component is real, onBind returns null because there is no NFC
// service to bind it, the packet callbacks are abstract and never invoked, and
// sendResponsePacket has nowhere to send.
//
// Mirrors packages/modules/Nfc
// framework/java/android/nfc/cardemulation/HostNfcFService.java.
package android.nfc.cardemulation;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

public abstract class HostNfcFService extends Service {

    public static final String SERVICE_INTERFACE =
            "android.nfc.cardemulation.action.HOST_NFCF_SERVICE";
    public static final String SERVICE_META_DATA =
            "android.nfc.cardemulation.host_nfcf_service";

    /** AOSP: the RF link was lost. */
    public static final int DEACTIVATION_LINK_LOSS = 0;

    public HostNfcFService() {}

    /** Null: nothing can bind this, so claiming otherwise would mislead. */
    @Override
    public final IBinder onBind(Intent intent) {
        return null;
    }

    /** @see HostApduService#sendResponseApdu -- same reasoning. */
    public final void sendResponsePacket(byte[] responsePacket) {}

    public abstract byte[] processNfcFPacket(byte[] commandPacket, Bundle extras);

    public abstract void onDeactivated(int reason);
}
