package apklayer.host;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.job.IJobCallback;
import android.app.job.IJobService;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.robolectric.RuntimeEnvironment;

/**
 * Runs the jobs an app schedules (the service is bound through Services, as converted app code is; Robolectric's own bindService hands out no binder): after its minimum latency (or its period) the job's service is bound and started the way the system's JobSchedulerService does
 * (IJobService.startJob with parameters whose callback is ours). The end of the job (jobFinished from any thread, which ShadowHostJobService lets reach us, or onStartJob returning false)
 * unbinds and plans a periodic job again, or one that asked for it after its backoff. The hub holds the jobs (HubScheduler: constraints, deadline, the
 * failure count), so they outlive the process (persisted jobs are not restored after the hub ends). A running job is stopped (onStopJob) when the app
 * cancels or schedules it again and when the hub says a constraint went. Its JobWorkItems (enqueue) are queued here: dequeueWork hands them out,
 * completeWork drops them, and the job ends when none is left (JobServiceContext.doDequeueWork); one not completed comes again at the next run.
 * shortcut: the work queue lives in the app's process (JobSchedulerService keeps it in system_server), so work enqueued and not done is lost when it ends.
 */
final class HostJobs {
    private static final Map<Integer, Runnable> PLANNED = new HashMap<>();
    private static final Map<Integer, Integer> FAILURES = new HashMap<>();  // without a hub: job id: how many runs in a row asked to be rescheduled
    private static final Map<Integer, java.util.function.IntConsumer> RUNNING = new HashMap<>();  // job id: stops its run with a JobParameters stop reason
    private static final Map<Integer, java.util.ArrayDeque<android.app.job.JobWorkItem>> WORK = new HashMap<>();  // job id: work not yet completed, in order
    private static int nextWorkId = 1;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private HostJobs() {}

    static synchronized void plan(JobInfo job, long delayMs) {
        stopRun(job.getId(), JobParameters.STOP_REASON_CANCELLED_BY_APP);  // schedule() of a running job replaces it (JobSchedulerService.scheduleAsPackage)
        if (HubClient.connected()) { HubClient.current().scheduleJob(job, delayMs); return; }  // the hub holds it: it runs when no process of the app does (HubScheduler)
        Runnable old = PLANNED.remove(job.getId());
        if (old != null) MAIN.removeCallbacks(old);
        Runnable start = () -> start(job);
        PLANNED.put(job.getId(), start);
        MAIN.postDelayed(start, Math.max(delayMs, 0));
    }

    static void plan(JobInfo job) { plan(job, job.isPeriodic() ? job.getIntervalMillis() : job.getMinLatencyMillis()); }

    static synchronized void cancel(int jobId) {
        FAILURES.remove(jobId);
        WORK.remove(jobId);
        stopRun(jobId, JobParameters.STOP_REASON_CANCELLED_BY_APP);
        if (HubClient.connected()) { HubClient.current().cancelJob(jobId); return; }
        Runnable planned = PLANNED.remove(jobId);
        if (planned != null) MAIN.removeCallbacks(planned);
    }

    /** The delay before a job that asked to be rescheduled runs again: JobSchedulerService.getRescheduleJobForFailureLocked (linear or exponential from the job's initial backoff, at least 10 s, at most 5 h). */
    static long backoff(JobInfo job, int attempts) {
        long initial = Math.max(job.getInitialBackoffMillis(), JobInfo.MIN_BACKOFF_MILLIS);
        long delay = job.getBackoffPolicy() == JobInfo.BACKOFF_POLICY_LINEAR ? initial * attempts : (long) Math.scalb((double) initial, Math.min(attempts - 1, 30));
        return Math.min(delay, JobInfo.MAX_BACKOFF_DELAY_MILLIS);
    }

    /** The hub says the job is due. */
    static void run(JobInfo job) { start(job); }

    /** The hub says a constraint stopped holding: the run ends with onStopJob (its answer decides a retry). */
    static void stop(int jobId, int reason) { synchronized (HostJobs.class) { stopRun(jobId, reason); } }

    private static void stopRun(int jobId, int reason) {
        java.util.function.IntConsumer running = RUNNING.remove(jobId);
        if (running != null) running.accept(reason);
    }

    /** JobScheduler.enqueue: the work joins the job's queue; a job not yet planned or running is scheduled, a running one dequeues it as it goes. */
    static synchronized boolean enqueue(JobInfo job, android.app.job.JobWorkItem work) {
        boolean known = WORK.containsKey(job.getId());
        WORK.computeIfAbsent(job.getId(), id -> new java.util.ArrayDeque<>()).add(work);
        return !known;
    }

    private static synchronized android.app.job.JobWorkItem dequeue(int jobId, java.util.Set<Integer> delivered) {
        java.util.ArrayDeque<android.app.job.JobWorkItem> queue = WORK.get(jobId);
        if (queue == null) return null;
        for (android.app.job.JobWorkItem item : queue) {
            if (item.getWorkId() != 0 && delivered.contains(item.getWorkId())) continue;
            if (item.getWorkId() == 0) item.setWorkId(nextWorkId++);
            item.bumpDeliveryCount();
            delivered.add(item.getWorkId());
            return item;
        }
        return null;
    }

    private static synchronized boolean complete(int jobId, int workId, java.util.Set<Integer> delivered) {
        java.util.ArrayDeque<android.app.job.JobWorkItem> queue = WORK.get(jobId);
        if (queue == null || !delivered.remove(workId)) return false;
        queue.removeIf(item -> item.getWorkId() == workId);
        return true;
    }

    private static void start(JobInfo job) {
        synchronized (HostJobs.class) { PLANNED.remove(job.getId()); }
        Context app = RuntimeEnvironment.getApplication();
        ComponentName service = job.getService();
        ServiceConnection[] connection = new ServiceConnection[1];
        AtomicBoolean ended = new AtomicBoolean();
        int[] notification = {0, JobService.JOB_END_NOTIFICATION_POLICY_DETACH};  // the id setNotification posted (0: none) and what happens to it when the job ends
        int[] stopReason = {0};  // set when the run was stopped (onStopJob): cancelled or replaced by the app, or a constraint went
        java.util.Set<Integer> delivered = new java.util.HashSet<>();  // work ids dequeued in this run and not yet completed
        JobParameters[] parameters = new JobParameters[1];
        IJobService[] service2 = new IJobService[1];
        var callback = new IJobCallback.Stub() {
            /** Unbind, then plan the job again or forget it (JobServiceContext, JobSchedulerService.onJobCompletedLocked). Once per run. */
            private void end(boolean reschedule) {
                if (ended.getAndSet(true)) return;
                MAIN.post(() -> {
                    synchronized (HostJobs.class) { if (stopReason[0] == 0) RUNNING.remove(job.getId()); }
                    if (notification[0] != 0 && notification[1] == JobService.JOB_END_NOTIFICATION_POLICY_REMOVE) app.getSystemService(NotificationManager.class).cancel(notification[0]);
                    boolean cancelled = stopReason[0] == JobParameters.STOP_REASON_CANCELLED_BY_APP, systemStop = stopReason[0] != 0;
                    if (Boolean.getBoolean("apklayer.logcat")) System.out.println("[apklayer] job " + job.getId() + " finished" + (cancelled ? ", cancelled" : reschedule ? ", again" : job.isPeriodic() ? ", next period" : ""));
                    if (cancelled) { /* the app's cancel or newer schedule stands */ }
                    else if (reschedule) {
                        if (HubClient.connected()) HubClient.current().retryJob(job.getId(), systemStop);  // the hub counts the failures (they outlive this process)
                        else { int attempts; synchronized (HostJobs.class) { attempts = FAILURES.merge(job.getId(), 1, Integer::sum); } plan(job, backoff(job, attempts)); }
                    } else {
                        synchronized (HostJobs.class) { FAILURES.remove(job.getId()); }
                        boolean more;  // work enqueued after the last dequeue: the job runs again for it, as JobSchedulerService does
                        synchronized (HostJobs.class) { java.util.ArrayDeque<?> queue = WORK.get(job.getId()); more = queue != null && !queue.isEmpty(); if (!more) WORK.remove(job.getId()); }
                        if (job.isPeriodic() || more) plan(job);
                        else app.getSystemService(JobScheduler.class).cancel(job.getId());
                    }
                    // last: the job's record is settled first, as JobSchedulerService completes it before JobServiceContext unbinds (the service's
                    // destroy idles the main looper, and what runs there must see the job ended)
                    try { Services.unbindService(app, connection[0]); } catch (RuntimeException gone) { /* already unbound */ }
                });
            }
            @Override public void jobFinished(int jobId, boolean reschedule) { end(reschedule); }
            @Override public void acknowledgeStartMessage(int jobId, boolean ongoing) { if (!ongoing) end(false); }  // onStartJob returned false: the job is done
            @Override public void setNotification(int jobId, int notificationId, Notification shown, int jobEndNotificationPolicy) {
                app.getSystemService(NotificationManager.class).notify(notificationId, shown);  // a user-initiated job's notification (JobServiceContext posts it for the app)
                notification[0] = notificationId; notification[1] = jobEndNotificationPolicy;
            }
            @Override public void acknowledgeGetTransferredDownloadBytesMessage(int jobId, int workId, long transferredBytes) {}
            @Override public void acknowledgeGetTransferredUploadBytesMessage(int jobId, int workId, long transferredBytes) {}
            @Override public void acknowledgeStopMessage(int jobId, boolean reschedule) { end(reschedule); }
            @Override public android.app.job.JobWorkItem dequeueWork(int jobId) {
                if (ended.get() || stopReason[0] != 0) return null;
                android.app.job.JobWorkItem work = dequeue(jobId, delivered);
                synchronized (HostJobs.class) { if (work == null && delivered.isEmpty()) end(false); }  // the last work is done: the job is (doDequeueWork)
                return work;
            }
            @Override public boolean completeWork(int jobId, int workId) { return complete(jobId, workId, delivered); }
            @Override public void handleAbandonedJob(int jobId) {}
            @Override public void updateEstimatedNetworkBytes(int jobId, android.app.job.JobWorkItem item, long downloadBytes, long uploadBytes) {}
            @Override public void updateTransferredNetworkBytes(int jobId, android.app.job.JobWorkItem item, long downloadBytes, long uploadBytes) {}
        };
        connection[0] = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                try {
                    if (ended.get()) return;  // stopped before its service came up
                    if (IJobService.Stub.asInterface(binder) == null) { System.out.println("[apklayer] job " + job.getId() + ": the service gave " + binder + " (" + (binder == null ? null : binder.getClass()) + "), not an IJobService"); return; }
                    parameters[0] = new JobParameters(callback, null, job.getId(), job.getExtras(), job.getTransientExtras(), job.getClipData(), job.getClipGrantFlags(),
                            false, job.isExpedited(), job.isUserInitiated(), null, null, job.getRequiredNetwork() != null ? app.getSystemService(android.net.ConnectivityManager.class).getActiveNetwork() : null);  // the network that meets the constraint (DownloadProvider fetches through it)
                    service2[0] = IJobService.Stub.asInterface(binder);
                    service2[0].startJob(parameters[0]);
                    if (Boolean.getBoolean("apklayer.logcat")) System.out.println("[apklayer] job " + job.getId() + " started on " + service.flattenToShortString());
                } catch (android.os.RemoteException | RuntimeException failure) { System.out.println("[apklayer] job " + job.getId() + " could not start: " + failure); }
            }
            @Override public void onServiceDisconnected(ComponentName name) {}
        };
        synchronized (HostJobs.class) {
            if (RUNNING.containsKey(job.getId())) { System.out.println("[apklayer] job " + job.getId() + " is already running"); return; }
            // a stop: onStopJob with the reason (JobServiceContext.sendStopMessageLocked); a run whose service is not up yet just ends
            RUNNING.put(job.getId(), reason -> MAIN.post(() -> {
                stopReason[0] = reason;
                if (service2[0] == null) { callback.end(false); return; }
                parameters[0].setStopReason(reason, 0, "apklayer");
                try { service2[0].stopJob(parameters[0]); } catch (android.os.RemoteException | RuntimeException failure) { callback.end(false); }
            }));
        }
        if (!Services.bindService(app, new Intent().setComponent(service), connection[0], Context.BIND_AUTO_CREATE)) System.out.println("[apklayer] job " + job.getId() + ": no service " + service.flattenToShortString());
    }
}
