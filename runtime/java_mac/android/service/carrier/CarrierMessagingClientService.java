package android.service.carrier;

import android.content.Intent;
import android.os.IBinder;

/**
 * API 29 marker service a carrier app declares so the platform keeps it alive
 * to receive carrier messages. It has no callbacks at all -- being bound IS the
 * whole contract.
 *
 * See the package note on CarrierIdentifier: nothing binds it here.
 *
 * Source: frameworks/base/telephony/java/android/service/carrier/CarrierMessagingClientService.java
 */
public class CarrierMessagingClientService extends android.app.Service {

    public CarrierMessagingClientService() { }

    /** AOSP declares this final and @NonNull, returning a no-op binder. Ours
     *  returns null, matching android.app.Service in this tree; a caller that
     *  cannot bind sees the same "not bound" state either way. */
    @Override public final IBinder onBind(Intent intent) { return null; }
}
