package hello.push;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

/** One phase from -Dhello.push: register (the default), alarm (an RTC_WAKEUP alarm in 8 s), job (a job in 6 s), jobs (JobsCheck), retry (RetryJob), service (a started service). */
public class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("push");
        setContentView(view);
        String phase = System.getProperty("hello.push", "register");
        switch (phase) {
            case "alarm" -> {
                PendingIntent operation = PendingIntent.getBroadcast(this, 1, new Intent(this, AlarmReceiver.class).setAction("hello.push.ALARM"), PendingIntent.FLAG_IMMUTABLE);
                getSystemService(AlarmManager.class).set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 8000, operation);
                System.out.println("[push] ALARM-SET");
            }
            case "job" -> {
                getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(7, new ComponentName(this, PushJob.class)).setMinimumLatency(6000).build());
                System.out.println("[push] JOB-SET");
            }
            case "jobs" -> JobsCheck.start(this);
            case "retry" -> {  // RetryJob: WorkManager's retry with a linear 10 s backoff
                getSharedPreferences("retry", MODE_PRIVATE).edit().clear().commit();
                getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(8, new ComponentName(this, RetryJob.class))
                        .setBackoffCriteria(RetryJob.BACKOFF_MS, JobInfo.BACKOFF_POLICY_LINEAR).build());
                System.out.println("[push] RETRY-SET");
            }
            case "service" -> {
                startService(new Intent(this, KeepService.class));
                System.out.println("[push] SERVICE-ASKED");
            }
            default -> System.out.println("[push] REGISTERED");
        }
    }
}
