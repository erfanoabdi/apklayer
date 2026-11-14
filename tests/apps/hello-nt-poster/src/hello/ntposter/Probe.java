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
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (code == 2) {  // the groups this process lists: id, name, their channels; and that a group asked for by id is the same
                    java.util.List<android.app.NotificationChannelGroup> groups = manager.getNotificationChannelGroups();
                    android.app.NotificationChannelGroup grp = manager.getNotificationChannelGroup("grp");
                    reply.writeString(groups.size() != 1 || grp == null ? "groups " + groups.size() + " grp " + grp
                            : groups.get(0).getId() + " " + groups.get(0).getName() + " [" + groups.get(0).getChannels().stream().map(NotificationChannel::getId).collect(java.util.stream.Collectors.joining(",")) + "], same as here "
                            + (grp.getChannels().size() == 1 ? 1 : 0));
                    return true;
                }
                if (code != 1) return false;
                NotificationChannel kept = manager.getNotificationChannel("kept");
                reply.writeString(kept == null ? "kept missing" : "kept " + kept.getName() + " " + kept.getImportance() + ", gone " + (manager.getNotificationChannel("gone") != null)
                        + ", listed " + manager.getNotificationChannels().stream().anyMatch(c -> c.getId().equals("kept")));
                return true;
            }
        };
    }
}
