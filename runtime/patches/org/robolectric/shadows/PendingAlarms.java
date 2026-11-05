package org.robolectric.shadows;

import android.app.PendingIntent;

/**
 * apklayer patch (patches/robolectric/0020): where ShadowAlarmManager sends the PendingIntent alarms of an auto-scheduling environment: the instance hub
 * (HostAlarms), which holds them beyond the life of the app's process, as AlarmManagerService does.
 */
public final class PendingAlarms {
  private PendingAlarms() {}

  /** The instance hub's side. */
  public interface Hook {
    /** True when the hub took the alarm (fires {@code delayMs} from now, then every {@code intervalMs} when positive); false: it is scheduled in the process. */
    boolean set(PendingIntent operation, long delayMs, long intervalMs);

    void cancel(PendingIntent operation);
  }

  public static volatile Hook hook;
}
