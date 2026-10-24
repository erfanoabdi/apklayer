// android.os.PowerMonitorReadings (API 35) -- a batch of energy readings, one
// per requested PowerMonitor, delivered to
// SystemHealthManager.getPowerMonitorReadings()'s OutcomeReceiver.
//
// AOSP ref: frameworks/base/core/java/android/os/PowerMonitorReadings.java
// (android16-release). Ported verbatim, including the binary search on
// PowerMonitor.index and the two documented "not in this batch" answers:
// ENERGY_UNAVAILABLE (-1) for energy, 0 for the timestamp. The @SystemApi
// GRANULARITY_* half is omitted -- it is not in the public API-36 surface.
package android.os;

import java.util.Arrays;
import java.util.Comparator;

public final class PowerMonitorReadings {
    public static final int ENERGY_UNAVAILABLE = -1;

    private final PowerMonitor[] mPowerMonitors;
    private final long[] mEnergyUws;
    private final long[] mTimestampsMs;

    private static final Comparator<PowerMonitor> POWER_MONITOR_COMPARATOR =
            new Comparator<PowerMonitor>() {
                @Override public int compare(PowerMonitor a, PowerMonitor b) {
                    return Integer.compare(a.index, b.index);
                }
            };

    public PowerMonitorReadings(PowerMonitor[] powerMonitors, long[] energyUws,
                                long[] timestampsMs) {
        mPowerMonitors = powerMonitors;
        mEnergyUws = energyUws;
        mTimestampsMs = timestampsMs;
    }

    public long getConsumedEnergy(PowerMonitor powerMonitor) {
        int offset = Arrays.binarySearch(mPowerMonitors, powerMonitor, POWER_MONITOR_COMPARATOR);
        if (offset >= 0) {
            return mEnergyUws[offset];
        }
        return ENERGY_UNAVAILABLE;
    }

    public long getTimestampMillis(PowerMonitor powerMonitor) {
        int offset = Arrays.binarySearch(mPowerMonitors, powerMonitor, POWER_MONITOR_COMPARATOR);
        if (offset >= 0) {
            return mTimestampsMs[offset];
        }
        return 0;
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(" monitors: [");
        for (int i = 0; i < mPowerMonitors.length; i++) {
            if (i != 0) sb.append(", ");
            sb.append(mPowerMonitors[i].getName())
              .append(" = ").append(mEnergyUws[i])
              .append(" (").append(mTimestampsMs[i]).append(')');
        }
        sb.append("]");
        return sb.toString();
    }
}
