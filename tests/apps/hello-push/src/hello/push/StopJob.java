package hello.push;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Job 11: keeps running until it is stopped (the app cancels it): onStopJob with the reason. */
public class StopJob extends JobService {
    @Override public boolean onStartJob(JobParameters parameters) {
        System.out.println("[push] STARTED 11");
        return true;
    }

    @Override public boolean onStopJob(JobParameters parameters) {
        System.out.println("[push] STOPPED 11 reason=" + parameters.getStopReason());
        return true;  // asks for a retry: a cancelled job gets none
    }
}
