package hello.system;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;

/** NotificationManager: a channel, a posted notification found among the active ones with its text, then cancelled. */
final class NotificationsCheck implements Check {
    @Override public String run(Context context) throws Exception {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("check", "Check", NotificationManager.IMPORTANCE_DEFAULT));
        Check.require(nm.getNotificationChannel("check") != null, "channel not stored");
        Check.require(nm.areNotificationsEnabled(), "notifications are disabled for the app");
        Notification n = new Notification.Builder(context, "check").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Apklayer check").setContentText("hello").build();
        nm.notify(4242, n);
        String seen = null;
        for (int i = 0; i < 20 && seen == null; i++) {
            for (StatusBarNotification s : nm.getActiveNotifications()) if (s.getId() == 4242) seen = String.valueOf(s.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE));
            if (seen == null) Thread.sleep(250);
        }
        Check.require(seen != null, "the notification is not among the active ones");
        nm.cancel(4242);
        return "title '" + seen + "'";
    }
}
