package android.service.carrier;

import android.content.Intent;
import android.os.IBinder;
import android.os.PersistableBundle;

/**
 * Base for a carrier app's config provider: the platform asks it for the
 * carrier-config bundle whenever the SIM or subscription changes.
 *
 * See the package note on CarrierIdentifier -- there is no SIM and no
 * subscription here, so onLoadConfig is never called.
 *
 * Constant transcribed verbatim from /tmp/aosp_api/android16.txt:
 *   CARRIER_SERVICE_INTERFACE = "android.service.carrier.CarrierService"
 *   (note the name is CARRIER_SERVICE_INTERFACE, not SERVICE_INTERFACE).
 *
 * Source: frameworks/base/telephony/java/android/service/carrier/CarrierService.java
 */
public abstract class CarrierService extends android.app.Service {

    public static final String CARRIER_SERVICE_INTERFACE =
            "android.service.carrier.CarrierService";

    public CarrierService() { }

    /** @deprecated at API 30 in favour of the subscription-id overload. */
    @Deprecated
    public abstract PersistableBundle onLoadConfig(CarrierIdentifier id);

    /** API 30. AOSP's default forwards to the deprecated overload so a service
     *  that only implemented the old one keeps working. Mirror the forwarding
     *  exactly. */
    public PersistableBundle onLoadConfig(int subscriptionId, CarrierIdentifier id) {
        return onLoadConfig(id);
    }

    /** @deprecated at API 30 in favour of the subscription-id overload. */
    @Deprecated
    public final void notifyCarrierNetworkChange(boolean active) { }

    /** API 34. There is no radio to tell about a network change. */
    public final void notifyCarrierNetworkChange(int subscriptionId, boolean active) { }

    /** AOSP is @CallSuper and returns the config binder for the carrier-config
     *  action. android.app.Service.onBind already returns null in this tree. */
    @Override public IBinder onBind(Intent intent) { return null; }
}
