package hello.notify;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

/**
 * Posts every kind of notification the phone's shell has to show (tools/halium/phone-checks.sh group notify reads them from the shell's notification service):
 * titles are "nf <kind>". Channels high/def/low/min/silent; basic (content, 2 actions, delete, auto cancel, large icon), bigtext, progress (updated in place),
 * group (two children and the summary), ongoing, a foreground service, one cancelled after 7 s. Receivers print "[nf] received <action>".
 */
public class Main extends Activity {
    private NotificationManager nm;

    private PendingIntent broadcast(String action, int code) {
        return PendingIntent.getBroadcast(this, code, new Intent(action).setPackage(getPackageName()), PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification.Builder base(String channel, String title, String text) {
        return new Notification.Builder(this, channel).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("notify");
        setContentView(view);
        nm = getSystemService(NotificationManager.class);
        System.out.println("[nf] permission " + checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) + " enabled=" + nm.areNotificationsEnabled());
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) { System.out.println("[nf] received " + intent.getAction()); }
        };
        IntentFilter filter = new IntentFilter();
        for (String action : new String[] {"nf.OPEN", "nf.ACT1", "nf.ACT2", "nf.DEL"}) filter.addAction(action);
        registerReceiver(receiver, filter, RECEIVER_EXPORTED);
        nm.createNotificationChannel(new NotificationChannel("high", "High", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel("def", "Default", NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel("low", "Low", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel("min", "Min", NotificationManager.IMPORTANCE_MIN));
        NotificationChannel silent = new NotificationChannel("silent", "Silent", NotificationManager.IMPORTANCE_DEFAULT);
        silent.setSound(null, null); silent.enableVibration(false);
        nm.createNotificationChannel(silent);
        NotificationChannel vib = new NotificationChannel("vib", "Vibrating", NotificationManager.IMPORTANCE_HIGH);
        vib.enableVibration(true); vib.setVibrationPattern(new long[] {0, 200, 100, 200});
        nm.createNotificationChannel(vib);

        Bitmap large = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        large.eraseColor(Color.rgb(200, 40, 40));
        nm.notify(1, base("high", "nf basic", "basic text").setLargeIcon(large).setSubText("sub").setShowWhen(true).setAutoCancel(true)
                .setContentIntent(broadcast("nf.OPEN", 1)).setDeleteIntent(broadcast("nf.DEL", 4))
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_send), "First", broadcast("nf.ACT1", 2)).build())
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_send), "Second", broadcast("nf.ACT2", 3)).build()).build());
        nm.notify(2, base("def", "nf bigtext", "short").setStyle(new Notification.BigTextStyle().bigText("the long text of the big text style, longer than one line of a banner so the shell has to wrap or cut it")).build());
        nm.notify(3, base("def", "nf progress", "30 percent").setProgress(100, 30, false).setOnlyAlertOnce(true).build());
        nm.notify(4, base("low", "nf low", "low importance").build());
        nm.notify(5, base("silent", "nf silent", "no sound").build());
        nm.notify(6, base("vib", "nf vibrate", "vibration pattern").build());
        nm.notify(7, base("def", "nf child A", "in group").setGroup("nfg").build());
        nm.notify(8, base("def", "nf child B", "in group").setGroup("nfg").build());
        nm.notify(9, base("def", "nf summary", "2 in group").setGroup("nfg").setGroupSummary(true).build());
        nm.notify(10, base("low", "nf ongoing", "ongoing").setOngoing(true).build());
        nm.notify(11, base("def", "nf cancel me", "gone after 7 s").build());
        startForegroundService(new Intent(this, Fg.class));
        Handler main = new Handler(Looper.getMainLooper());
        main.postDelayed(() -> {
            nm.notify(3, base("def", "nf progress", "done").setProgress(0, 0, false).setOnlyAlertOnce(true).build());
            System.out.println("[nf] progress updated");
            nm.notify(1, base("high", "nf basic", "replaced text").setAutoCancel(true).setContentIntent(broadcast("nf.OPEN", 1)).build());
            System.out.println("[nf] basic replaced");
        }, 4000);
        main.postDelayed(() -> { nm.cancel(11); System.out.println("[nf] cancelled 11 active=" + nm.getActiveNotifications().length); }, 7000);
        System.out.println("[nf] POSTED active=" + nm.getActiveNotifications().length);
    }
}
