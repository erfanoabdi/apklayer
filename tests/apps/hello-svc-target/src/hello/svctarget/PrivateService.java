package hello.svctarget;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class PrivateService extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { System.out.println("[svc-t] PRIVATE STARTED"); return START_NOT_STICKY; }
}
