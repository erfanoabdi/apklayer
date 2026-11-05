package hello.push;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Process;

/** A job that came due with no process of the app running. */
public class PushJob extends JobService {
    @Override public boolean onStartJob(JobParameters parameters) {
        System.out.println("[push] JOB " + parameters.getJobId() + " pid=" + Process.myPid());
        return false;
    }

    @Override public boolean onStopJob(JobParameters parameters) { return false; }
}
