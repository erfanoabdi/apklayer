package apklayer.host;

import android.app.PendingIntent;
import org.robolectric.shadows.PendingAlarms;

/** PendingIntent alarms go to the instance hub (HubScheduler), which fires them with every app process gone; without a hub they stay in the process. */
final class HostAlarms implements PendingAlarms.Hook {
    private HostAlarms() {}

    static void install() { PendingAlarms.hook = new HostAlarms(); }

    @Override public boolean set(PendingIntent operation, long delayMs, long intervalMs) {
        if (!HubClient.connected()) return false;
        try { HubClient.current().setAlarm(operation, delayMs, intervalMs); return true; }
        catch (RuntimeException refused) { System.out.println("[apklayer] alarm kept in the process: " + refused); return false; }  // not a PendingIntent of the hub
    }

    @Override public void cancel(PendingIntent operation) {
        if (!HubClient.connected()) return;
        try { HubClient.current().cancelAlarm(operation); }
        catch (RuntimeException ignored) { /* not the hub's, or the hub is gone */ }
    }
}
