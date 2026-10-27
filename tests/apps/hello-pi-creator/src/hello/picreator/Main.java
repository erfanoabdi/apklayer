package hello.picreator;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Process;
import android.widget.TextView;

/** Creates PendingIntents and hands them to hello.piholder, another app that sends them. The scenario comes from -Dhello.pi=... */
public class Main extends Activity {
    private Intent ping, ping2;

    private static void require(boolean yes, String detail) { if (!yes) throw new AssertionError(detail); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("pi creator");
        setContentView(view);
        try { start(System.getProperty("hello.pi", "broadcast")); }
        catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[pi] FAIL " + failure); }
    }

    private interface Check { void run(Intent report) throws Exception; }

    private void registerReport(Check check) {
        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                new Thread(() -> {
                    try { check.run(intent); } catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[pi] FAIL " + failure); }
                }).start();
            }
        }, new IntentFilter("hello.pi.REPORT"), RECEIVER_EXPORTED);
    }

    /** {count, last text} of hello.svctarget's counter service, through a short-lived binding. */
    private Object[] serviceState() {
        try {
            java.util.concurrent.CountDownLatch connected = new java.util.concurrent.CountDownLatch(1);
            android.os.IBinder[] binder = new android.os.IBinder[1];
            android.content.ServiceConnection connection = new android.content.ServiceConnection() {
                @Override public void onServiceConnected(ComponentName name, android.os.IBinder service) { binder[0] = service; connected.countDown(); }
                @Override public void onServiceDisconnected(ComponentName name) { }
            };
            bindService(new Intent().setComponent(new ComponentName("hello.svctarget", "hello.svctarget.CounterService")), connection, Context.BIND_AUTO_CREATE);
            require(connected.await(15, java.util.concurrent.TimeUnit.SECONDS), "no connection");
            android.os.Parcel data = android.os.Parcel.obtain(), reply = android.os.Parcel.obtain();
            binder[0].transact(1, data, reply, 0);
            Object[] state = {reply.readInt(), reply.readString()};
            unbindService(connection);
            return state;
        } catch (Exception failure) { throw new RuntimeException(failure); }
    }

    private void give(PendingIntent first, PendingIntent second) {
        sendBroadcast(new Intent("hello.pi.GIVE").setComponent(new ComponentName("hello.piholder", "hello.piholder.GiveReceiver")).putExtra("creator", getPackageName())
                .putExtra("pi1", first).putExtra("pi2", second));
    }

    private void start(String scenario) {
        switch (scenario) {
            case "broadcast" -> {
                registerReceiver(new BroadcastReceiver() {
                    @Override public void onReceive(Context context, Intent intent) { ping = intent; System.out.println("[pi] PING code-less extras fill=" + intent.getStringExtra("fill")); }
                }, new IntentFilter("hello.pi.PING"), RECEIVER_EXPORTED);
                registerReceiver(new BroadcastReceiver() {
                    @Override public void onReceive(Context context, Intent intent) { ping2 = intent; System.out.println("[pi] PING2 fill=" + intent.getStringExtra("fill")); }
                }, new IntentFilter("hello.pi.PING2"), RECEIVER_EXPORTED);
                registerReceiver(new BroadcastReceiver() {
                    @Override public void onReceive(Context context, Intent intent) {
                        try {
                            require(ping != null && "x".equals(ping.getStringExtra("fill")), "mutable PendingIntent did not take the fill-in extra: " + ping);
                            require(ping2 != null && ping2.getStringExtra("fill") == null, "immutable PendingIntent took a fill-in extra: " + ping2);
                            require(intent.getBooleanExtra("canceled", false), "a one-shot PendingIntent was sent twice");
                            require(getPackageName().equals(intent.getStringExtra("creatorPackage")) && intent.getIntExtra("holderPid", 0) != Process.myPid(), "creator identity");
                            System.out.println("[pi] PASS broadcast: sent by another app (pid " + intent.getIntExtra("holderPid", 0) + "), fill-in, immutable, one-shot, creator package");
                        } catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[pi] FAIL " + failure); }
                    }
                }, new IntentFilter("hello.pi.REPORT"), RECEIVER_EXPORTED);
                PendingIntent first = PendingIntent.getBroadcast(this, 1, new Intent("hello.pi.PING").setPackage(getPackageName()), PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ONE_SHOT);
                PendingIntent second = PendingIntent.getBroadcast(this, 2, new Intent("hello.pi.PING2").setPackage(getPackageName()), PendingIntent.FLAG_IMMUTABLE);
                give(first, second);
            }
            case "targets" -> {
                registerReport(intent -> {
                    Object[] state = serviceState();
                    require((int) state[0] == 1 && "viaPI".equals(state[1]), "service state " + java.util.Arrays.toString(state));
                    System.out.println("[pi] PASS targets: a service and an activity of other apps started through PendingIntents sent by a third app");
                });
                Intent service = new Intent().setComponent(new ComponentName("hello.svctarget", "hello.svctarget.CounterService")).putExtra("text", "viaPI");
                Intent activity = new Intent().setComponent(new ComponentName("hello.acttarget", "hello.acttarget.PickActivity")).putExtra("text", "viaPI");
                give(PendingIntent.getService(this, 3, service, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT), PendingIntent.getActivity(this, 4, activity, PendingIntent.FLAG_IMMUTABLE));
            }
            case "cancel" -> {
                registerReport(intent -> {
                    require(intent.getBooleanExtra("firstCanceled", false), "a canceled PendingIntent was sent");
                    System.out.println("[pi] PASS cancel: a canceled PendingIntent cannot be sent");
                });
                PendingIntent first = PendingIntent.getBroadcast(this, 8, new Intent("hello.pi.PING").setPackage(getPackageName()), PendingIntent.FLAG_IMMUTABLE);
                first.cancel();
                give(first, null);
            }
            default -> System.out.println("[pi] unknown scenario");
        }
    }
}
