package hello.svccaller;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Scenario "stoplock": a background thread holds a lock and calls stopSelf(); onDestroy takes the same lock (Meet's pattern). stopSelf must not wait
 * for onDestroy: ActivityManager answers at once and destroys the service later on the main thread. */
public class LockService extends Service {
    static final Object LOCK = new Object();
    static final java.util.concurrent.CountDownLatch DESTROYED = new java.util.concurrent.CountDownLatch(1);
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(() -> { synchronized (LOCK) { stopSelf(); try { Thread.sleep(300); } catch (InterruptedException ignored) {} } }, "stoplock-holder").start();
        return START_NOT_STICKY;
    }
    @Override public void onDestroy() { synchronized (LOCK) { DESTROYED.countDown(); } }
    @Override public IBinder onBind(Intent intent) { return null; }
}
