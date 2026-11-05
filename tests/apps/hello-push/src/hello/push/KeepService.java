package hello.push;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** A started service (microG's GCM service is one): it runs until stopped. */
public class KeepService extends Service {
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        System.out.println("[push] SERVICE started");
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
