package hello.ntposter;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;

/** The app's other process (":probe"): transaction 1 answers how this process sees the channels Main made ("kept" and the deleted "gone"). */
public class Probe extends Service {
    @Override public IBinder onBind(Intent intent) {
        return new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                if (code != 1) return false;
                NotificationManager manager = getSystemService(NotificationManager.class);
                NotificationChannel kept = manager.getNotificationChannel("kept");
                reply.writeString(kept == null ? "kept missing" : "kept " + kept.getName() + " " + kept.getImportance() + ", gone " + (manager.getNotificationChannel("gone") != null)
                        + ", listed " + manager.getNotificationChannels().stream().anyMatch(c -> c.getId().equals("kept")));
                return true;
            }
        };
    }
}
