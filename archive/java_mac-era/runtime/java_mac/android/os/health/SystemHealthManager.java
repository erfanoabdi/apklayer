// android.os.health.SystemHealthManager
//
// AOSP's battery/CPU health-stats service. Apps fetch it via getSystemService(
// SYSTEM_HEALTH_SERVICE) and call takeMyUidSnapshot() to read aggregate power
// metrics. Mac.apk collects no health stats, so the snapshot is an empty
// HealthStats -- the honest "no health metrics" result. Mirrors
// frameworks/base/core/java/android/os/health/SystemHealthManager.java.
package android.os.health;

import android.content.Context;

public class SystemHealthManager {

    public SystemHealthManager() {}
    public SystemHealthManager(Context context) {}

    /** AOSP takeMyUidSnapshot(): aggregate health stats for this UID. We have
     *  none, so the returned object is empty. Typed as Object because the
     *  HealthStats type is not modeled; apps that read it find no buckets. */
    public android.os.health.HealthStats takeMyUidSnapshot() { return new android.os.health.HealthStats(); }
    public android.os.health.HealthStats takeUidSnapshot(int uid) { return new android.os.health.HealthStats(); }
    public android.os.health.HealthStats[] takeUidSnapshots(int[] uids) {
        android.os.health.HealthStats[] r = new android.os.health.HealthStats[uids != null ? uids.length : 0];
        for (int i = 0; i < r.length; i++) r[i] = new android.os.health.HealthStats();
        return r;
    }

    // ---- CPU/GPU headroom (API 36) ----------------------------------------
    // AOSP core/java/android/os/health/SystemHealthManager.java:178-362. EVERY
    // one of these seven begins with
    //     if (mHintManager == null || mHintManagerClientData == null
    //             || !...supportInfo.headroom.isCpuSupported) {
    //         throw new UnsupportedOperationException();
    //     }
    // and each is documented "@throws UnsupportedOperationException if the CPU
    // [GPU] headroom API is unsupported". Headroom comes from the ADPF power
    // HAL, which macOS does not have -- so this host is exactly the
    // unsupported device AOSP describes, and the throw is the FAITHFUL answer.
    // Returning 0f (or 100f, or an invented window range) would tell a game's
    // frame scheduler it had measured the device, which is the one thing a
    // headroom API must never do wrong.

    public float getCpuHeadroom(android.os.CpuHeadroomParams params) {
        throw new UnsupportedOperationException();
    }

    public float getGpuHeadroom(android.os.GpuHeadroomParams params) {
        throw new UnsupportedOperationException();
    }

    public android.util.Pair<Integer, Integer> getCpuHeadroomCalculationWindowRange() {
        throw new UnsupportedOperationException();
    }

    public android.util.Pair<Integer, Integer> getGpuHeadroomCalculationWindowRange() {
        throw new UnsupportedOperationException();
    }

    public long getCpuHeadroomMinIntervalMillis() {
        throw new UnsupportedOperationException();
    }

    public long getGpuHeadroomMinIntervalMillis() {
        throw new UnsupportedOperationException();
    }

    public int getMaxCpuHeadroomTidsSize() {
        throw new UnsupportedOperationException();
    }

    // ---- Power monitors (API 35) ------------------------------------------
    // AOSP :509 -- when the platform has no PowerStats service the supported
    // list is `List.of()`, delivered through the executor when one is given.
    // That is a normal, non-exceptional answer ("this device exposes no energy
    // meters"), unlike the headroom family above.
    public void getSupportedPowerMonitors(java.util.concurrent.Executor executor,
            java.util.function.Consumer<java.util.List<android.os.PowerMonitor>> onResult) {
        final java.util.List<android.os.PowerMonitor> result = java.util.Collections.emptyList();
        if (executor != null) {
            executor.execute(new Runnable() {
                @Override public void run() { onResult.accept(result); }
            });
        } else {
            onResult.accept(result);
        }
    }

    // AOSP :565 -- with no PowerStats service, EVERY requested monitor is
    // unknown, and AOSP reports that through the receiver's ERROR channel with
    // an IllegalArgumentException("Unsupported power monitor"); it does not
    // deliver an empty reading set. Since getSupportedPowerMonitors() above
    // returns an empty list, a well-behaved caller never reaches this with a
    // non-empty request.
    public void getPowerMonitorReadings(java.util.List<android.os.PowerMonitor> powerMonitors,
            java.util.concurrent.Executor executor,
            android.os.OutcomeReceiver<android.os.PowerMonitorReadings, RuntimeException> onResult) {
        final IllegalArgumentException error =
                new IllegalArgumentException("Unsupported power monitor");
        if (executor != null) {
            executor.execute(new Runnable() {
                @Override public void run() { onResult.onError(error); }
            });
        } else {
            onResult.onError(error);
        }
    }
}

