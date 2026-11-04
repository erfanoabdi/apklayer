package hello.svccaller;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

/** Scenario "own": a service of the caller's own package in its process; its binder answers transaction 1 with the pid it runs in and whether that process created the multiprocess ProbeProvider. */
public class PidService extends Service {
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
