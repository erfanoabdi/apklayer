package apklayer.host;

import android.app.JobSchedulerImpl;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobWorkItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowJobScheduler;

/**
 * Robolectric's job scheduler keeps the jobs and never runs them: here they also run (HostJobs). The pending jobs are one list for the process,
 * as JobSchedulerService keeps one per app: Robolectric kept one per JobScheduler object, and each context has its own, so a job that HostJobs
 * cancelled at its end stayed pending for the context that scheduled it.
 */
@Implements(value = JobSchedulerImpl.class, isInAndroidSdk = false)
public class ShadowHostJobScheduler extends ShadowJobScheduler.ShadowJobSchedulerImpl {
    private static final Map<Integer, JobInfo> PENDING = new LinkedHashMap<>();

    @Override @Implementation public int schedule(JobInfo job) {
        int result = super.schedule(job);  // Robolectric's failOnJob and failExpeditedJob still apply
        if (result != JobScheduler.RESULT_SUCCESS) return result;
        synchronized (PENDING) { PENDING.put(job.getId(), job); }
        HostJobs.plan(job);
        return result;
    }

    @Override @Implementation public int enqueue(JobInfo job, JobWorkItem work) { return schedule(job); }  // Robolectric's enqueue calls schedule too

    @Override @Implementation public void cancel(int jobId) {
        HostJobs.cancel(jobId);
        synchronized (PENDING) { PENDING.remove(jobId); }
        super.cancel(jobId);
    }

    @Override @Implementation public void cancelAll() {
        for (JobInfo job : getAllPendingJobs()) cancel(job.getId());  // HostJobs' plans (and the hub's) too
        super.cancelAll();
    }

    @Override @Implementation public List<JobInfo> getAllPendingJobs() { synchronized (PENDING) { return new ArrayList<>(PENDING.values()); } }

    @Override @Implementation public JobInfo getPendingJob(int jobId) { synchronized (PENDING) { return PENDING.get(jobId); } }
}
