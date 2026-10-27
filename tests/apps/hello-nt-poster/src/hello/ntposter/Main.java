package hello.ntposter;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.TextView;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Posts a notification whose content, action and delete intents are broadcasts to this app; the test driver clicks on the desktop popup. */
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
        manager.createNotificationChannel(new NotificationChannel("main", "Main", NotificationManager.IMPORTANCE_DEFAULT));
        Notification notification = new Notification.Builder(this, "main").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("apklayer test").setContentText("hello from nt-poster")
                .setContentIntent(broadcast("hello.nt.OPEN", 1)).addAction(new Notification.Action.Builder(null, "Act", broadcast("hello.nt.ACT", 2)).build())
                .setDeleteIntent(broadcast("hello.nt.DEL", 3)).build();
        manager.notify(7, notification);
        System.out.println("[nt] POSTED");
    }
}
