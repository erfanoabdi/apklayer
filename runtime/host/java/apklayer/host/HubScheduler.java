package apklayer.host;

import android.app.job.JobInfo;
import android.content.Intent;
import android.os.IBinder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Alarms and jobs held by the hub, which outlives every app process (AlarmManagerService and JobSchedulerService live in system_server): the app records
 * them here, and when one is due the hub starts the app's process and fires the PendingIntent (the alarm's operation, sent as AlarmManagerService's
 * DeliveryTracker does: with EXTRA_ALARM_COUNT) or runs the job in the app (ENDPOINT_JOB). They are kept for the life of the hub; the delays are
 * relative to the call (the app computed them against its own clock). Exact-alarm permission rules and Doze are not modelled.
 */
final class HubScheduler {
    private static final long MIN_INTERVAL_MS = 60_000;  // AlarmManagerService.Constants.DEFAULT_MIN_INTERVAL: a shorter repeat is expanded
    private static final long MAX_INTERVAL_MS = 365L * 24 * 3600 * 1000;

    private final HubPendingIntents pendingIntents;
    private final HubBroadcasts broadcasts;
    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, task -> { Thread t = new Thread(task, "hub-scheduler"); t.setDaemon(true); return t; });
    private final ExecutorService firing = Executors.newCachedThreadPool(task -> { Thread t = new Thread(task, "hub-scheduler-fire"); t.setDaemon(true); return t; });
    private final Map<Object, ScheduledFuture<?>> planned = new HashMap<>();

    HubScheduler(HubPendingIntents pendingIntents, HubBroadcasts broadcasts) {
        this.pendingIntents = pendingIntents; this.broadcasts = broadcasts; timer.setRemoveOnCancelPolicy(true);
    }

    /** AlarmManager.set*(operation): fires {@code delayMs} from now, and every {@code intervalMs} after when that is positive. */
    void setAlarm(HubDirectory.Peer caller, IBinder operation, long delayMs, long intervalMs) {
        Object[] info = pendingIntents.info(operation);
        if (info == null || !caller.name().equals(info[0])) throw new SecurityException("Not " + caller.name() + "'s PendingIntent");
        if (intervalMs > 0 && intervalMs < MIN_INTERVAL_MS) intervalMs = MIN_INTERVAL_MS;
        else if (intervalMs > MAX_INTERVAL_MS) intervalMs = MAX_INTERVAL_MS;
        long delay = Math.max(delayMs, 0);
        System.out.println("[apklayer-hub] alarm of " + caller.name() + " in " + delay + " ms every " + intervalMs + " ms");
        List<Object> key = List.of("alarm", operation);
        plan(key, delay, intervalMs, () -> {
            // AlarmManagerService.DeliveryTracker.deliverLocked: the operation, sent with the count of expirations
            int status = pendingIntents.send(operation, 0, new Intent().putExtra(Intent.EXTRA_ALARM_COUNT, 1), null, null);
            System.out.println("[apklayer-hub] alarm of " + caller.name() + " fired, status " + status);
            if (status < 0) cancel(key);  // the PendingIntent was canceled (or is one-shot and spent)
        });
    }

    void cancelAlarm(HubDirectory.Peer caller, IBinder operation) {
        Object[] info = pendingIntents.info(operation);
        if (info != null && caller.name().equals(info[0])) cancel(List.of("alarm", operation));
    }

    /** JobScheduler.schedule: the job starts in the app {@code delayMs} from now (the app's own HostJobs runs it; a periodic or retried job plans itself again). */
    void scheduleJob(HubDirectory.Peer caller, JobInfo job, long delayMs) {
        String pkg = caller.name();
        System.out.println("[apklayer-hub] job " + job.getId() + " of " + pkg + " in " + delayMs + " ms");
        plan(List.of("job", pkg, job.getId()), Math.max(delayMs, 0), 0, () -> {
            HubDirectory.Peer target = broadcasts.process(pkg);
            if (target == null) { System.out.println("[apklayer-hub] could not start " + pkg + " for job " + job.getId()); return; }
            HubProtocol.call(target.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_JOB, request -> request.writeTypedObject(job, 0), reply -> null);
        });
    }

    void cancelJob(HubDirectory.Peer caller, int jobId) { cancel(List.of("job", caller.name(), jobId)); }

    private void plan(Object key, long delayMs, long intervalMs, Runnable action) {
        Runnable run = () -> firing.execute(() -> { try { action.run(); } catch (RuntimeException failure) { System.out.println("[apklayer-hub] scheduled work failed: " + failure); } });
        synchronized (planned) {
            ScheduledFuture<?> old = planned.remove(key);
            if (old != null) old.cancel(false);
            ScheduledFuture<?> future = intervalMs > 0 ? timer.scheduleAtFixedRate(run, delayMs, intervalMs, TimeUnit.MILLISECONDS) : timer.schedule(run, delayMs, TimeUnit.MILLISECONDS);
            planned.put(key, future);
        }
    }

    private void cancel(Object key) {
        synchronized (planned) { ScheduledFuture<?> old = planned.remove(key); if (old != null) old.cancel(false); }
    }
}
