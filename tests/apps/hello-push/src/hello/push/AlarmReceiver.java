package hello.push;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

/** An alarm (AlarmManager.set with a broadcast PendingIntent) that fired with no process of the app running. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        System.out.println("[push] ALARM " + intent.getAction() + " pid=" + Process.myPid() + " count=" + intent.getIntExtra(Intent.EXTRA_ALARM_COUNT, -1));
        Notify.post(context, 2, "push-alarm", "alarm fired");
    }
}
