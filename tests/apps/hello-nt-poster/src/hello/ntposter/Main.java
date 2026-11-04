package hello.ntposter;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.TextView;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Posts a notification whose content, action and delete intents are broadcasts to this app; the test driver clicks on the desktop popup (phase click),
 * or the app cancels it (cancel), or the user turned the app's notifications off (muted). Phase from -Dhello.nt.
 */
public class Main extends Activity {
    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    private PendingIntent broadcast(String action, int code) {
        return PendingIntent.getBroadcast(this, code, new Intent(action).setPackage(getPackageName()), PendingIntent.FLAG_IMMUTABLE);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("nt poster");
        setContentView(view);
        String phase = System.getProperty("hello.nt", "click");
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                seen.add(intent.getAction());
                System.out.println("[nt] received " + intent.getAction());
                if (seen.containsAll(Set.of("hello.nt.OPEN", "hello.nt.ACT", "hello.nt.DEL"))) System.out.println("[nt] PASS click: content, action and delete intents sent by the hub");
            }
        };
        IntentFilter filter = new IntentFilter();
        for (String action : new String[] {"hello.nt.OPEN", "hello.nt.ACT", "hello.nt.DEL"}) filter.addAction(action);
        registerReceiver(receiver, filter, RECEIVER_EXPORTED);
        NotificationManager manager = getSystemService(NotificationManager.class);
        // what apps check before they ask the user to turn notifications on (NotificationManagerCompat): the user's switch, not a test default
        boolean muted = phase.equals("muted");
        String status = "enabled=" + manager.areNotificationsEnabled() + " importance=" + manager.getImportance();
        System.out.println("[nt] status " + status);
        if (manager.areNotificationsEnabled() == muted || (manager.getImportance() == NotificationManager.IMPORTANCE_NONE) != muted) { System.out.println("[nt] FAIL status " + status); return; }
        try {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            System.out.println("[nt] FAIL no ActivityNotFoundException for an intent no installed app handles");
            return;
        } catch (ActivityNotFoundException expected) { System.out.println("[nt] ActivityNotFoundException as on Android: " + expected.getMessage()); }
        manager.createNotificationChannel(new NotificationChannel("main", "Main", NotificationManager.IMPORTANCE_DEFAULT));
        Notification notification = new Notification.Builder(this, "main").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("apklayer test").setContentText("hello from nt-poster")
                .setContentIntent(broadcast("hello.nt.OPEN", 1)).addAction(new Notification.Action.Builder(null, "Act", broadcast("hello.nt.ACT", 2)).build())
                .setDeleteIntent(broadcast("hello.nt.DEL", 3)).build();
        manager.notify(7, notification);
        System.out.println("[nt] POSTED " + phase);
        if (muted) System.out.println(manager.getActiveNotifications().length == 0 ? "[nt] PASS muted: " + status + ", nothing posted" : "[nt] FAIL muted: a notification is active");
        if (phase.equals("cancel")) new Handler(Looper.getMainLooper()).postDelayed(() -> { manager.cancel(7); System.out.println("[nt] CANCELLED"); }, 1500);
    }
}
