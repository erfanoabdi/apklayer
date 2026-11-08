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

/** One phase from -Dhello.push: register (the default), alarm (an RTC_WAKEUP alarm in 8 s), job (a job in 6 s), service (a started service), c2dm (a registration with microG). */
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
            case "service" -> {
                startService(new Intent(this, KeepService.class));
                System.out.println("[push] SERVICE-ASKED");
            }
            case "c2dm" -> {
                // as Firebase's Rpc (the registration every FCM app makes, FirebaseMessaging.getToken): REGISTER to Play services / microG, the answer
                // through a Messenger
                android.os.Messenger reply = new android.os.Messenger(new android.os.Handler(android.os.Looper.getMainLooper(), message -> {
                    Bundle extras = message.obj instanceof Intent answer ? answer.getExtras() : null;
                    String token = extras == null ? null : extras.getString("registration_id");
                    System.out.println("[push] C2DM " + (token != null ? "token of " + token.length() + " chars" : "error=" + (extras == null ? message : extras.getString("error"))));
                    return true;
                }));
                Intent register = new Intent("com.google.android.c2dm.intent.REGISTER").setPackage("com.google.android.gms")
                        .putExtra("app", PendingIntent.getBroadcast(this, 0, new Intent().setPackage("com.google.example.invalidpackage"), PendingIntent.FLAG_IMMUTABLE))
                        .putExtra("sender", System.getProperty("hello.sender", "760348033671")).putExtra("scope", "*").putExtra("google.messenger", reply);
                System.out.println("[push] C2DM-ASKED " + startService(register));
            }
            default -> System.out.println("[push] REGISTERED");
        }
    }
}
