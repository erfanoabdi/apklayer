package apklayer.host;

import android.os.Binder;
import android.os.Parcel;

/**
 * The instance's "batterystats" (system_server's BatteryStatsService) as a sink: apklayer keeps no battery accounting. Its native clients note
 * events and ignore the answer (SensorService's BatteryService notes every sensor enable and disable); without the service each note waited 5 s in
 * libbinder's getService, so every SensorManager.registerListener and unregisterListener took 5 s. Every call is answered with no exception and
 * no result (a Java caller reads zeros). Published only when the stack runs SensorService, its one client here.
 */
final class HubBatteryStats extends Binder {
    static final String SERVICE = "batterystats";
    private static final String DESCRIPTOR = "com.android.internal.app.IBatteryStats";

    private HubBatteryStats() { attachInterface(null, DESCRIPTOR); }

    static void publishIfSensors() {
        if (HostSensorShadows.declared() && !RealBinder.publish(SERVICE, new HubBatteryStats()))
            System.out.println("[apklayer-hub] cannot publish " + SERVICE);
    }

    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
        if (code == INTERFACE_TRANSACTION) { reply.writeString(DESCRIPTOR); return true; }
        if (reply != null) reply.writeNoException();
        return true;
    }
}
