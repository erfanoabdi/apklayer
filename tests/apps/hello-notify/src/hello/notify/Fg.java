package hello.notify;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** A foreground service: its notification (id 90, ongoing) is what the shell's notification list shows while the service runs. */
public class Fg extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(90, new Notification.Builder(this, "low").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("nf foreground").setContentText("service running").build());
        System.out.println("[nf] foreground service started");
        return START_NOT_STICKY;
    }
}
