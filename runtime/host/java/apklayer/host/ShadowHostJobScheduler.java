package apklayer.host;

import android.app.JobSchedulerImpl;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobWorkItem;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowJobScheduler;

/** Robolectric's job scheduler keeps the jobs and never runs them: here they also run (HostJobs). */
@Implements(value = JobSchedulerImpl.class, isInAndroidSdk = false)
public class ShadowHostJobScheduler extends ShadowJobScheduler.ShadowJobSchedulerImpl {
    @Override @Implementation public int schedule(JobInfo job) {
        int result = super.schedule(job);
        if (result == JobScheduler.RESULT_SUCCESS) HostJobs.plan(job);
        return result;
    }

    @Override @Implementation public int enqueue(JobInfo job, JobWorkItem work) {
        int result = super.enqueue(job, work);
        if (result == JobScheduler.RESULT_SUCCESS) HostJobs.plan(job);
        return result;
    }

    @Override @Implementation public void cancel(int jobId) {
        HostJobs.cancel(jobId);
        super.cancel(jobId);
    }
}
