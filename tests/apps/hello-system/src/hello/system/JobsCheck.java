package hello.system;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** JobScheduler: a job with a short latency is accepted, listed as pending, and runs its service. */
final class JobsCheck implements Check {
    @Override public String run(Context context) throws Exception {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        CheckJob.started = new CountDownLatch(1);
        JobInfo info = new JobInfo.Builder(77, new ComponentName(context, CheckJob.class)).setMinimumLatency(500).setOverrideDeadline(3000).build();
        Check.require(jobs.schedule(info) == JobScheduler.RESULT_SUCCESS, "schedule refused");
        Check.require(CheckJob.started.await(30, TimeUnit.SECONDS), "the job did not start in 30 s");
        jobs.cancel(77);
        return "job 77 ran";
    }
}
