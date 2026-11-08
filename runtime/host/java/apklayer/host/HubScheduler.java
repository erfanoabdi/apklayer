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
 * A job is a JobStatus of JobSchedulerService: it waits for its constraints (network, charging, battery and storage not low: HubJobConstraints) after its
 * delay, unless its deadline passed; it is stopped (onStopJob) when one stops holding while it runs; the hub keeps its failure count (backoff), so it
 * survives the app's process, and a new schedule() starts it anew.
 */
final class HubScheduler {
    private static final long MIN_INTERVAL_MS = 60_000;  // AlarmManagerService.Constants.DEFAULT_MIN_INTERVAL: a shorter repeat is expanded
    private static final long MAX_INTERVAL_MS = 365L * 24 * 3600 * 1000;

    private final HubDirectory directory;
    private final HubPendingIntents pendingIntents;
    private final HubBroadcasts broadcasts;
    private final ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, task -> { Thread t = new Thread(task, "hub-scheduler"); t.setDaemon(true); return t; });
    private final ExecutorService firing = Executors.newCachedThreadPool(task -> { Thread t = new Thread(task, "hub-scheduler-fire"); t.setDaemon(true); return t; });
    private final Map<Object, ScheduledFuture<?>> planned = new HashMap<>();
    // QuotaController's rate limiting window (DEFAULT_RATE_LIMITING_WINDOW_MS, DEFAULT_MAX_JOB_COUNT_PER_RATE_LIMITING_WINDOW): at most 20 job starts per package a minute
    private static final long RATE_WINDOW_MS = 60_000;
    private static final int RATE_MAX_STARTS = 20;
    private final Map<String, java.util.ArrayDeque<Long>> starts = new HashMap<>();
    private final Map<List<Object>, Job> jobs = new HashMap<>();

    /** A scheduled job: due (its delay passed, it waits for its constraints), running (sent to the app, not yet ended), past its deadline, its failure counts. */
    private static final class Job {
        final String pkg; final JobInfo info;
        boolean due, running, pastDeadline; int failures, systemStops;
        Job(String pkg, JobInfo info) { this.pkg = pkg; this.info = info; }
    }

    HubScheduler(HubDirectory directory, HubPendingIntents pendingIntents, HubBroadcasts broadcasts) {
        this.directory = directory; this.pendingIntents = pendingIntents; this.broadcasts = broadcasts; timer.setRemoveOnCancelPolicy(true);
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

    /** JobScheduler.schedule (a new job, or a periodic one's next period): due {@code delayMs} from now; its deadline counts from now too. */
    void scheduleJob(HubDirectory.Peer caller, JobInfo info, long delayMs) {
        String pkg = caller.name();
        System.out.println("[apklayer-hub] job " + info.getId() + " of " + pkg + " in " + delayMs + " ms");
        List<Object> key = List.of("job", pkg, info.getId());
        Job job = new Job(pkg, info);
        synchronized (jobs) { jobs.put(key, job); }
        long delay = Math.max(delayMs, 0);
        plan(key, delay, 0, () -> due(key, job));
        if (!info.isPeriodic() && info.getMaxExecutionDelayMillis() > 0) {  // setOverrideDeadline: it runs then whatever its constraints
            plan(List.of("deadline", key), Math.max(delay, info.getMaxExecutionDelayMillis()), 0, () -> {
                synchronized (jobs) { job.pastDeadline = true; if (jobs.get(key) != job || !job.due) return; }
                start(key, job);
            });
        }
    }

    /**
     * The job's run ended asking to be rescheduled (jobFinished(true), or onStopJob true): after its backoff, as JobSchedulerService.getRescheduleJobForFailureLocked
     * counts it (system stops, a constraint gone, count a third), with no deadline. Unknown: rescheduled meanwhile or cancelled, the newer state stands.
     */
    void retryJob(HubDirectory.Peer caller, int jobId, boolean systemStop) {
        List<Object> key = List.of("job", caller.name(), jobId);
        Job job;
        int attempts;
        synchronized (jobs) {
            job = jobs.get(key);
            if (job == null) return;
            job.running = false; job.due = false; job.pastDeadline = false;
            if (systemStop) job.systemStops++; else job.failures++;
            attempts = Math.max(1, job.failures + job.systemStops / 3);
        }
        cancel(List.of("deadline", key));
        long delay = HostJobs.backoff(job.info, attempts);
        System.out.println("[apklayer-hub] job " + jobId + " of " + caller.name() + " again in " + delay + " ms (attempt " + attempts + ")");
        plan(key, delay, 0, () -> due(key, job));
    }

    /** Its delay passed: it runs now if its constraints hold (or its deadline passed), else when they do (constraintsChanged). */
    private void due(List<Object> key, Job job) {
        int unmet = HubJobConstraints.unmet(job.info);
        synchronized (jobs) {
            if (jobs.get(key) != job) return;
            job.due = true;
            if (unmet != 0 && !job.pastDeadline) { System.out.println("[apklayer-hub] job " + job.info.getId() + " of " + job.pkg + " waits for its constraints (" + unmet + ")"); return; }
        }
        start(key, job);
    }

    private void start(List<Object> key, Job job) {
        long wait = rateLimited(job.pkg);
        if (wait > 0) {  // over the quota: the job waits until the window frees (an app that schedules itself again at once, as WorkManager does with unmet constraints, would loop)
            System.out.println("[apklayer-hub] job " + job.info.getId() + " of " + job.pkg + " over its quota, waits " + wait + " ms");
            plan(key, wait, 0, () -> start(key, job));
            return;
        }
        synchronized (jobs) { if (jobs.get(key) != job || job.running) return; job.due = false; job.running = true; }
        HubDirectory.Peer target = broadcasts.process(job.pkg);
        if (target == null) { System.out.println("[apklayer-hub] could not start " + job.pkg + " for job " + job.info.getId()); return; }
        HubProtocol.call(target.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_JOB, request -> request.writeTypedObject(job.info, 0), reply -> null);
    }

    /** The host's network or power changed: waiting jobs whose constraints now hold start, running ones whose constraint went are stopped (JobServiceContext). */
    void constraintsChanged() {
        List<Map.Entry<List<Object>, Job>> all;
        synchronized (jobs) { all = List.copyOf(jobs.entrySet()); }
        for (Map.Entry<List<Object>, Job> entry : all) {
            Job job = entry.getValue();
            int unmet = HubJobConstraints.unmet(job.info);
            if (job.due && unmet == 0) firing.execute(() -> start(entry.getKey(), job));
            else if (job.running && unmet != 0 && !job.pastDeadline) {
                HubDirectory.Peer target = directory.peer(job.pkg);
                System.out.println("[apklayer-hub] job " + job.info.getId() + " of " + job.pkg + " stops: constraint " + unmet + " gone");
                synchronized (jobs) { job.running = false; }
                if (target != null) firing.execute(() -> HubProtocol.call(target.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_JOB_STOP,
                        request -> { request.writeInt(job.info.getId()); request.writeInt(unmet); }, reply -> null));
            }
        }
    }

    /** 0 and the start is counted, or the wait in ms until a start leaves the window. */
    private long rateLimited(String pkg) {
        long now = System.currentTimeMillis();
        synchronized (starts) {
            java.util.ArrayDeque<Long> times = starts.computeIfAbsent(pkg, key -> new java.util.ArrayDeque<>());
            while (!times.isEmpty() && times.peekFirst() <= now - RATE_WINDOW_MS) times.removeFirst();
            if (times.size() >= RATE_MAX_STARTS) return times.peekFirst() + RATE_WINDOW_MS - now;
            times.addLast(now);
            return 0;
        }
    }

    void cancelJob(HubDirectory.Peer caller, int jobId) {
        List<Object> key = List.of("job", caller.name(), jobId);
        synchronized (jobs) { jobs.remove(key); }
        cancel(key); cancel(List.of("deadline", key));
    }

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
