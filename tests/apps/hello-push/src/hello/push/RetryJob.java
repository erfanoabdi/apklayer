package hello.push;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;

/**
 * Job 8, the way WorkManager's SystemJobService retries work (Result.retry): it keeps its own attempt count and enqueue time and asks the scheduler to
 * reschedule (jobFinished(true)), whose backoff (linear, 10 s per attempt, the JobInfo's criteria) must match its own count even when the app's process
 * ends between runs; a run before its own next time is "executed before schedule" (EARLY: WorkManager defers it again). Done after its third run.
 */
public class RetryJob extends JobService {
    static final long BACKOFF_MS = 10_000;

    @Override public boolean onStartJob(JobParameters parameters) {
        SharedPreferences state = getSharedPreferences("retry", MODE_PRIVATE);
        int attempt = state.getInt("attempt", 0);
        long now = System.currentTimeMillis(), due = state.getLong("enqueued", 0) + BACKOFF_MS * attempt;
        if (attempt > 0 && now < due - 1000) {
            System.out.println("[push] EARLY 8 attempt " + attempt + " by " + (due - now) + " ms");
            jobFinished(parameters, true);
            return true;
        }
        attempt++;
        state.edit().putInt("attempt", attempt).putLong("enqueued", now).commit();
        System.out.println("[push] RUN 8 attempt " + attempt);
        if (attempt >= 3) { state.edit().clear().commit(); System.out.println("[push] DONE 8"); }
        jobFinished(parameters, attempt < 3);
        return true;
    }

    @Override public boolean onStopJob(JobParameters parameters) { return true; }
}
