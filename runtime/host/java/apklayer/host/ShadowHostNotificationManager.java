package apklayer.host;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowNotificationManager;

/** With the hub, the app's notifications also go to the hub, which owns them for every app and shows them on the desktop. The in-process state stays for the app's own queries. */
@Implements(value = NotificationManager.class, isInAndroidSdk = false)
public class ShadowHostNotificationManager extends ShadowNotificationManager {
    private static boolean hub() { return HubClient.connected(); }

    @Implementation @Override protected void notify(int id, Notification notification) { notify(null, id, notification); }

    @Implementation @Override protected void notify(String tag, int id, Notification notification) {
        super.notify(tag, id, notification);
        if (!hub()) return;
        Context context = RuntimeEnvironment.getApplication();
        NotificationChannel channel = notification.getChannelId() == null ? null : getNotificationChannel(notification.getChannelId());
        HubClient.current().postNotification(context.getPackageName(), tag, id, notification, channel == null ? NotificationManager.IMPORTANCE_DEFAULT : channel.getImportance());
    }

    @Implementation @Override protected void cancel(int id) { cancel(null, id); }

    @Implementation @Override protected void cancel(String tag, int id) {
        super.cancel(tag, id);
        if (hub()) HubClient.current().cancelNotification(tag, id, false);
    }

    @Implementation @Override protected void cancelAll() {
        super.cancelAll();
        if (hub()) HubClient.current().cancelNotification(null, 0, true);
    }
}
