package hello.system;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.CountDownLatch;

/** The job service JobsCheck schedules. */
public final class CheckJob extends JobService {
    static volatile CountDownLatch started = new CountDownLatch(1);

    @Override public boolean onStartJob(JobParameters params) { started.countDown(); return false; }

    @Override public boolean onStopJob(JobParameters params) { return false; }
}
