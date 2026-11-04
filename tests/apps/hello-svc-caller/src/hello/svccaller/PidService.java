package hello.svccaller;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

/** Scenario "own": a service of the caller's own package in its process; its binder answers transaction 1 with the pid it runs in and whether that process created the multiprocess ProbeProvider. */
public class PidService extends Service {
    /** Scenario "framework": start commands and destroys of the in-process instance. */
    static final java.util.concurrent.CountDownLatch STARTED = new java.util.concurrent.CountDownLatch(1);
    static volatile int destroyed;
    @Override public int onStartCommand(Intent intent, int flags, int startId) { STARTED.countDown(); return START_NOT_STICKY; }
    @Override public void onDestroy() { destroyed++; }
    @Override public IBinder onBind(Intent intent) {
        return new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                reply.writeInt(android.os.Process.myPid());
                reply.writeInt(ProbeProvider.created ? 1 : 0);
                return true;
            }
        };
    }
}
