package hello.push;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

/** A notification the way a messenger shows a new message. */
final class Notify {
    private Notify() {}

    static void post(Context context, int id, String title, String text) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("messages", "Messages", NotificationManager.IMPORTANCE_HIGH));
        manager.notify(id, new Notification.Builder(context, "messages").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text).build());
    }
}
