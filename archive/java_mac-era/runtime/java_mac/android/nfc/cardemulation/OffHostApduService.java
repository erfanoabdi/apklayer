// android.nfc.cardemulation.OffHostApduService -- the marker Service an app
// publishes to declare AIDs routed to a SECURE ELEMENT rather than to the app.
//
// AOSP's own implementation is a marker: the service is never bound and never
// called, and onBind is abstract purely so the app declares one. Nothing about
// it depends on hardware, so this file is a faithful mirror rather than a
// degradation -- what is absent is the secure element the routing would point
// at, and a Mac has none, so no AID is ever routed.
//
// Mirrors packages/modules/Nfc
// framework/java/android/nfc/cardemulation/OffHostApduService.java.
package android.nfc.cardemulation;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public abstract class OffHostApduService extends Service {

    public static final String SERVICE_INTERFACE =
            "android.nfc.cardemulation.action.OFF_HOST_APDU_SERVICE";
    public static final String SERVICE_META_DATA =
            "android.nfc.cardemulation.off_host_apdu_service";

    public OffHostApduService() {}

    @Override
    public abstract IBinder onBind(Intent intent);
}
