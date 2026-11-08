package hello.push;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobWorkItem;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * The "jobs" phase (tests/vm/push.py jobs): job 10 gets three JobWorkItems (one more while it runs), job 11 runs until the app cancels it (onStopJob),
 * job 12 needs a cellular network the desktop does not have (it must not run), job 13 too but with a 3 s deadline (it runs then).
 */
final class JobsCheck {
    private JobsCheck() {}

    static void start(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        JobInfo work = new JobInfo.Builder(10, new ComponentName(context, WorkJob.class)).build();
        for (String name : new String[] {"a", "b", "c"}) jobs.enqueue(work, new JobWorkItem(new Intent().putExtra("name", name)));
        jobs.schedule(new JobInfo.Builder(11, new ComponentName(context, StopJob.class)).build());
        jobs.schedule(new JobInfo.Builder(12, new ComponentName(context, PushJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_CELLULAR).build());
        jobs.schedule(new JobInfo.Builder(13, new ComponentName(context, PushJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_CELLULAR).setOverrideDeadline(3000).build());
        System.out.println("[push] JOBS-SET");
        Handler main = new Handler(Looper.getMainLooper());
        main.postDelayed(() -> jobs.enqueue(work, new JobWorkItem(new Intent().putExtra("name", "d"))), 1500);
        main.postDelayed(() -> { jobs.cancel(11); System.out.println("[push] CANCELLED 11"); }, 3000);
    }
}
