package apklayer.host;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.os.BatteryManager;
import java.io.File;

/**
 * Whether a job's constraints hold on the host, as JobSchedulerService's controllers decide them: the network request against the host's network
 * (ConnectivityController: NetworkRequest.canBeSatisfiedBy), charging and battery not low from the power supplies (BatteryController: low is at most 15 %
 * on battery), storage not low for the instance's data (DeviceStorageMonitorService: under 5 % or 500 MB free).
 * shortcut: device idle counts as met (the hub does not know the screen state), and storage changes are seen only with a network or power event.
 */
final class HubJobConstraints {
    private static final long STORAGE_LOW_MAX = 500L << 20;

    private HubJobConstraints() {}

    /** 0 when every constraint holds, else the JobParameters stop reason of the first that does not. */
    static int unmet(JobInfo job) {
        if (job.getRequiredNetwork() != null) {
            HostNetwork.State network = HostNetwork.read();
            if (network == null || !job.getRequiredNetwork().canBeSatisfiedBy(HostNetwork.capabilities(network))) return JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY;
        }
        if (job.isRequireCharging() || job.isRequireBatteryNotLow()) {
            HostBattery.State power = HostBattery.read();
            boolean charging = power.plugged() != 0 && (!power.present() || power.status() == BatteryManager.BATTERY_STATUS_CHARGING || power.status() == BatteryManager.BATTERY_STATUS_FULL);
            if (job.isRequireCharging() && !charging) return JobParameters.STOP_REASON_CONSTRAINT_CHARGING;
            if (job.isRequireBatteryNotLow() && power.present() && power.plugged() == 0 && power.level() <= 15) return JobParameters.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW;
        }
        if (job.isRequireStorageNotLow()) {
            File data = new File(System.getProperty("apklayer.storage", "/"));
            if (data.getUsableSpace() < Math.min(data.getTotalSpace() / 20, STORAGE_LOW_MAX)) return JobParameters.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW;
        }
        return 0;
    }
}
