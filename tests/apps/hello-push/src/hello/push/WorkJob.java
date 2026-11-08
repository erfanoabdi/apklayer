package hello.push;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.app.job.JobWorkItem;

/** Job 10: takes its work items one by one (dequeueWork, completeWork), as JobIntentService does; the job ends when none is left. */
public class WorkJob extends JobService {
    @Override public boolean onStartJob(JobParameters parameters) {
        new Thread(() -> {
            for (JobWorkItem item; (item = parameters.dequeueWork()) != null; ) {
                try { Thread.sleep(700); } catch (InterruptedException stop) { return; }  // a work item takes its time: "d" arrives meanwhile
                System.out.println("[push] WORK " + item.getIntent().getStringExtra("name") + " delivery=" + item.getDeliveryCount());
                parameters.completeWork(item);
            }
        }, "work").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters parameters) { return true; }

    @Override public void onDestroy() { System.out.println("[push] WORK-DONE"); super.onDestroy(); }
}
