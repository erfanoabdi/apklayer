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
 * or the app cancels it (cancel), or the user turned the app's notifications off (muted); channels: the app's channels as its other process sees them.
 * Phase from -Dhello.nt.
 */
public class Main extends Activity {
    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    private PendingIntent broadcast(String action, int code) {
        return PendingIntent.getBroadcast(this, code, new Intent(action).setPackage(getPackageName()), PendingIntent.FLAG_IMMUTABLE);
    }

    /** Channels are per package (NotificationManagerService): a create again renames and only lowers the importance, a deleted one is gone, in every process. */
    private void channels() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("kept", "Kept", NotificationManager.IMPORTANCE_HIGH));
        manager.createNotificationChannel(new NotificationChannel("kept", "Kept 2", NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel("kept", "Kept 2", NotificationManager.IMPORTANCE_HIGH));  // raising it is the user's, not the app's
        manager.createNotificationChannel(new NotificationChannel("gone", "Gone", NotificationManager.IMPORTANCE_DEFAULT));
        manager.deleteNotificationChannel("gone");
        manager.createNotificationChannelGroup(new android.app.NotificationChannelGroup("grp", "Group"));  // groups are the app's, not this process's
        NotificationChannel inGroup = new NotificationChannel("inGroup", "In group", NotificationManager.IMPORTANCE_DEFAULT);
        inGroup.setGroup("grp");
        manager.createNotificationChannel(inGroup);
        bindService(new Intent(this, Probe.class), new android.content.ServiceConnection() {
            @Override public void onServiceConnected(android.content.ComponentName name, android.os.IBinder binder) {
                new Thread(() -> {
                    android.os.Parcel data = android.os.Parcel.obtain(), reply = android.os.Parcel.obtain();
                    try {
                        binder.transact(1, data, reply, 0);
                        String seen = reply.readString(), want = "kept Kept 2 " + NotificationManager.IMPORTANCE_LOW + ", gone false, listed true";
                        data.recycle(); data = android.os.Parcel.obtain(); reply.recycle(); reply = android.os.Parcel.obtain();
                        binder.transact(2, data, reply, 0);
                        String groups = reply.readString(), wantGroups = "grp Group [inGroup], same as here 1";
                        if (!groups.equals(wantGroups) || manager.getNotificationChannelGroups().size() != 1) { System.out.println("[nt] FAIL channel groups: the other process sees " + groups + ", not " + wantGroups); return; }
                        System.out.println(seen.equals(want) ? "[nt] PASS channels: the other process sees " + seen + "; groups " + groups : "[nt] FAIL channels: the other process sees " + seen + ", not " + want);
                    } catch (android.os.RemoteException e) { System.out.println("[nt] FAIL channels: " + e); }
                    finally { data.recycle(); reply.recycle(); }
                }).start();
            }
            @Override public void onServiceDisconnected(android.content.ComponentName name) {}
        }, BIND_AUTO_CREATE);
    }

    private Intent settingsIntent() { return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()); }

    /** The settings page the hub shows (tests/vm/hub.py clicks its first button twice): the first visit switches the notifications off, the second back on. */
    @Override protected void onActivityResult(int code, int result, Intent data) {
        boolean enabled = getSystemService(NotificationManager.class).areNotificationsEnabled();
        if (code == 1) {
            if (result != RESULT_OK || enabled) { System.out.println("[nt] FAIL settings: result " + result + ", notifications enabled " + enabled + " after Turn off"); return; }
            startActivityForResult(settingsIntent(), 2);
        } else if (code == 2) {
            System.out.println(result == RESULT_OK && enabled ? "[nt] PASS settings: the page resolved, Turn off muted the app, Turn on switched it back" : "[nt] FAIL settings: result " + result + ", enabled " + enabled + " after Turn on");
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("nt poster");
        setContentView(view);
        String phase = System.getProperty("hello.nt", "click");
        if (phase.equals("channels")) { channels(); return; }
        if (phase.equals("settings")) { startActivityForResult(settingsIntent(), 1); return; }
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
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
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
