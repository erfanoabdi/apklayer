package hello.svccaller;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

/** A service in its own process (":exit") that ends that process soon after its last client unbinds, as GeckoView's MediaManager does (scenario "respawn"). */
public class ExitService extends Service {
    @Override public IBinder onBind(Intent intent) {
        return new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                reply.writeInt(0); reply.writeString(""); reply.writeInt(0); reply.writeInt(android.os.Process.myPid());  // the shape of CounterService's state
                return true;
            }
        };
    }
    @Override public boolean onUnbind(Intent intent) {
        android.os.Process.killProcess(android.os.Process.myPid());  // the client's next bind reaches the hub while this process ends
        return false;
    }
}
