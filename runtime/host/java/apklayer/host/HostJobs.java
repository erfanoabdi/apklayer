package apklayer.host;

import android.app.job.IJobCallback;
import android.app.job.IJobService;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.HashMap;
import java.util.Map;
import org.robolectric.RuntimeEnvironment;

/**
 * Runs the jobs an app schedules: after its minimum latency (or its period) the job's service is bound and started the way the system's JobSchedulerService does
 * (IJobService.startJob with parameters whose callback is ours; jobFinished unbinds, reschedules a periodic job or one that asked for it). Constraints count as met, the
 * desktop has power and network; jobs live as long as the process (persisted jobs are not restored).
 */
final class HostJobs {
    private static final long BACKOFF_MS = 30_000;
    private static final Map<Integer, Runnable> PLANNED = new HashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private HostJobs() {}

    static synchronized void plan(JobInfo job, long delayMs) {
        cancel(job.getId());
        Runnable start = () -> start(job);
        PLANNED.put(job.getId(), start);
        MAIN.postDelayed(start, Math.max(delayMs, 0));
    }

    static void plan(JobInfo job) { plan(job, job.isPeriodic() ? job.getIntervalMillis() : job.getMinLatencyMillis()); }

    static synchronized void cancel(int jobId) {
        Runnable planned = PLANNED.remove(jobId);
        if (planned != null) MAIN.removeCallbacks(planned);
    }

    private static void start(JobInfo job) {
        synchronized (HostJobs.class) { PLANNED.remove(job.getId()); }
        Context app = RuntimeEnvironment.getApplication();
        ComponentName service = job.getService();
        ServiceConnection[] connection = new ServiceConnection[1];
        IJobCallback.Stub callback = new IJobCallback.Stub() {
            @Override public void jobFinished(int jobId, boolean reschedule) {
                MAIN.post(() -> {
                    try { app.unbindService(connection[0]); } catch (RuntimeException gone) { /* already unbound */ }
                    if (job.isPeriodic()) plan(job);
                    else if (reschedule) plan(job, BACKOFF_MS);
                    else app.getSystemService(JobScheduler.class).cancel(jobId);
                });
            }
            @Override public void acknowledgeGetTransferredDownloadBytesMessage(int jobId, int workId, long transferredBytes) {}
            @Override public void acknowledgeGetTransferredUploadBytesMessage(int jobId, int workId, long transferredBytes) {}
            @Override public void acknowledgeStartMessage(int jobId, boolean ongoing) {}
            @Override public void acknowledgeStopMessage(int jobId, boolean reschedule) {}
            @Override public android.app.job.JobWorkItem dequeueWork(int jobId) { return null; }
            @Override public boolean completeWork(int jobId, int workId) { return true; }
            @Override public void handleAbandonedJob(int jobId) {}
            @Override public void updateEstimatedNetworkBytes(int jobId, android.app.job.JobWorkItem item, long downloadBytes, long uploadBytes) {}
            @Override public void updateTransferredNetworkBytes(int jobId, android.app.job.JobWorkItem item, long downloadBytes, long uploadBytes) {}
            @Override public void setNotification(int jobId, int notificationId, android.app.Notification notification, int jobEndNotificationPolicy) {}
        };
        connection[0] = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                try {
                    JobParameters parameters = new JobParameters(callback, null, job.getId(), job.getExtras(), job.getTransientExtras(), job.getClipData(), job.getClipGrantFlags(),
                            false, job.isExpedited(), job.isUserInitiated(), null, null, null);
                    IJobService.Stub.asInterface(binder).startJob(parameters);
                    if (Boolean.getBoolean("apklayer.logcat")) System.out.println("[apklayer] job " + job.getId() + " started on " + service.flattenToShortString());
                } catch (android.os.RemoteException | RuntimeException failure) { System.out.println("[apklayer] job " + job.getId() + " could not start: " + failure); }
            }
            @Override public void onServiceDisconnected(ComponentName name) {}
        };
        if (!app.bindService(new Intent().setComponent(service), connection[0], Context.BIND_AUTO_CREATE)) System.out.println("[apklayer] job " + job.getId() + ": no service " + service.flattenToShortString());
    }
}
