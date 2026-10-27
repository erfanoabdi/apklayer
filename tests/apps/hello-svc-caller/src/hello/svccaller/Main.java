package hello.svccaller;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.widget.TextView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Starts, stops and inspects a started service of hello.svctarget (another app, started on demand by the hub). The scenario comes from -Dhello.svc=... */
public class Main extends Activity {
    static final ComponentName COUNTER = new ComponentName("hello.svctarget", "hello.svctarget.CounterService");

    private static void require(boolean yes, String detail) { if (!yes) throw new AssertionError(detail); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("svc caller");
        setContentView(view);
        new Thread(() -> {
            try { run(System.getProperty("hello.svc", "start")); }
            catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[svc] FAIL " + failure); }
        }, "svc-caller").start();
    }

    private Intent counter(String text) { return new Intent().setComponent(COUNTER).putExtra("text", text); }

    /** {count, last text, last start id, pid} of the running service instance, through a short-lived binding. */
    private Object[] state() throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        IBinder[] binder = new IBinder[1];
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { binder[0] = service; connected.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        require(bindService(new Intent().setComponent(COUNTER), connection, Context.BIND_AUTO_CREATE), "bind");
        require(connected.await(15, TimeUnit.SECONDS), "no connection");
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            require(binder[0].transact(1, data, reply, 0), "transact");
            return new Object[] {reply.readInt(), reply.readString(), reply.readInt(), reply.readInt()};
        } finally { data.recycle(); reply.recycle(); unbindService(connection); }
    }

    private void run(String scenario) throws Exception {
        switch (scenario) {
            case "start" -> {
                require(COUNTER.equals(startService(counter("a"))), "startService result");
                Object[] first = state();
                require((int) first[0] == 1 && "a".equals(first[1]), "first state " + java.util.Arrays.toString(first));
                startService(counter("b"));
                Object[] second = state();
                require((int) second[0] == 2 && "b".equals(second[1]) && (int) second[2] > (int) first[2] && (int) second[3] == (int) first[3], "second state " + java.util.Arrays.toString(second));
                require(stopService(new Intent().setComponent(COUNTER)), "stopService");
                Object[] fresh = state();  // a new instance: the old one was destroyed
                require((int) fresh[0] == 0, "service survived stopService " + java.util.Arrays.toString(fresh));
                System.out.println("[svc] PASS start: started in another app (pid " + first[3] + "), start ids, stopService destroys it");
            }
            case "selfstop" -> {
                startService(counter("s").putExtra("selfstop", true));
                Object[] fresh = state();
                require((int) fresh[0] == 0, "service survived stopSelf " + java.util.Arrays.toString(fresh));
                System.out.println("[svc] PASS selfstop: stopSelf in the service's app ended it");
            }
            case "sticky" -> {
                startService(counter("k").putExtra("mode", Service_START_STICKY));
                Object[] before = state();
                System.out.println("[svc] KILL " + before[3]);
                Thread.sleep(3000);
                Object[] after = null;
                for (int attempt = 0; attempt < 10; attempt++) {
                    after = state();
                    if ((int) after[0] >= 1) break;
                    Thread.sleep(1000);
                }
                require((int) after[3] != (int) before[3] && (int) after[0] == 1 && "(null)".equals(after[1]), "after the kill " + java.util.Arrays.toString(after));
                System.out.println("[svc] PASS sticky: restarted after the kill with a null intent, new pid " + after[3]);
            }
            case "private" -> {
                try { startService(new Intent().setComponent(new ComponentName("hello.svctarget", "hello.svctarget.PrivateService"))); require(false, "private service started"); }
                catch (SecurityException expected) { System.out.println("[svc] PASS private: " + expected.getMessage()); }
            }
            default -> System.out.println("[svc] unknown scenario");
        }
    }

    private static final int Service_START_STICKY = 1;
}
