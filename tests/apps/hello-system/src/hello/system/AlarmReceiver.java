package hello.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.concurrent.CountDownLatch;

/** The receiver an alarm of AlarmsCheck fires. */
public final class AlarmReceiver extends BroadcastReceiver {
    static volatile CountDownLatch fired = new CountDownLatch(1);

    @Override public void onReceive(Context context, Intent intent) { fired.countDown(); }
}
