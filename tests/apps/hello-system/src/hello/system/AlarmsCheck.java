package hello.system;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** AlarmManager: an alarm 1.5 s ahead fires its broadcast; a cancelled one does not. */
final class AlarmsCheck implements Check {
    @Override public String run(Context context) throws Exception {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent fire = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmReceiver.fired = new CountDownLatch(1);
        long due = System.currentTimeMillis() + 1500;
        alarms.set(AlarmManager.RTC_WAKEUP, due, fire);
        Check.require(AlarmReceiver.fired.await(15, TimeUnit.SECONDS), "the alarm did not fire in 15 s");
        long late = System.currentTimeMillis() - due;
        PendingIntent never = PendingIntent.getBroadcast(context, 2, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmReceiver.fired = new CountDownLatch(1);
        alarms.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, never);
        alarms.cancel(never);
        Check.require(!AlarmReceiver.fired.await(3, TimeUnit.SECONDS), "a cancelled alarm fired");
        return "fired " + late + " ms after its time";
    }
}
