package hello.system;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CountDownLatch;

/**
 * The asynchronous job AsyncJobsCheck schedules, the way WorkManager's SystemJobService works: onStartJob returns true and a worker thread calls
 * stopSelf (must not throw) and then jobFinished. Extra "retries": how many runs ask to be rescheduled before one finishes for good.
 */
public final class AsyncJob extends JobService {
    static volatile CountDownLatch started = new CountDownLatch(1);
    static volatile CountDownLatch destroyed = new CountDownLatch(1);  // counted on the main thread after the unbinding that destroyed the service
    static volatile int runs;
    static volatile String failure;

    @Override public boolean onStartJob(JobParameters params) {
        int run = ++runs;
        boolean reschedule = run <= params.getExtras().getInt("retries", 0);
        started.countDown();
        new Thread(() -> {
            try { stopSelf(); } catch (Throwable thrown) { failure = "stopSelf threw " + thrown; }
            jobFinished(params, reschedule);
        }, "async-job-" + params.getJobId()).start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return false; }

    @Override public void onDestroy() {
        super.onDestroy();
        CountDownLatch latch = destroyed;
        new Handler(Looper.getMainLooper()).post(latch::countDown);  // after the rest of HostJobs' end of the job (cancel or reschedule), which runs on the main thread too
    }
}
