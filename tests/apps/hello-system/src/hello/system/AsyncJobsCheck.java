package hello.system;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * JobScheduler, asynchronous jobs (AsyncJob: onStartJob returns true, jobFinished from a worker thread): a job that finishes is unbound and forgotten;
 * one that asks to be rescheduled runs again after its backoff (10 s, linear), then finishes. stopSelf in a job does not throw.
 */
final class AsyncJobsCheck implements Check {
    @Override public String run(Context context) throws Exception {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        long once = runJob(jobs, context, 78, 0);
        long twice = runJob(jobs, context, 79, 1);
        return "job 78 finished in " + once + " ms, job 79 rescheduled and finished in " + twice + " ms";
    }

    private static long runJob(JobScheduler jobs, Context context, int id, int retries) throws Exception {
        AsyncJob.runs = 0;
        AsyncJob.failure = null;
        AsyncJob.started = new CountDownLatch(retries + 1);
        AsyncJob.destroyed = new CountDownLatch(retries + 1);
        PersistableBundle extras = new PersistableBundle();
        extras.putInt("retries", retries);
        JobInfo info = new JobInfo.Builder(id, new ComponentName(context, AsyncJob.class)).setMinimumLatency(200).setExtras(extras)
                .setBackoffCriteria(10_000, JobInfo.BACKOFF_POLICY_LINEAR).build();
        long begin = System.currentTimeMillis();
        Check.require(jobs.schedule(info) == JobScheduler.RESULT_SUCCESS, "schedule refused");
        try {
            Check.require(AsyncJob.started.await(30, TimeUnit.SECONDS), "job " + id + " ran " + AsyncJob.runs + " of " + (retries + 1) + " times in 30 s");
            Check.require(AsyncJob.destroyed.await(10, TimeUnit.SECONDS), "job " + id + ": the service was not unbound after jobFinished");
            Check.require(AsyncJob.failure == null, "job " + id + ": " + AsyncJob.failure);
            Check.require(AsyncJob.runs == retries + 1, "job " + id + " ran " + AsyncJob.runs + " times, not " + (retries + 1));
            Check.require(jobs.getPendingJob(id) == null, "job " + id + " is still pending after it finished");
            return System.currentTimeMillis() - begin;
        } finally {
            jobs.cancel(id);
        }
    }
}
