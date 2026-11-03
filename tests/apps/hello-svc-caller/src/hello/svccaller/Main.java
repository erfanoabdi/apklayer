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
    private Object[] state() throws Exception { return state(COUNTER); }

    private Object[] state(ComponentName service) throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        IBinder[] binder = new IBinder[1];
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { binder[0] = service; connected.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        require(bindService(new Intent().setComponent(service), connection, Context.BIND_AUTO_CREATE), "bind");
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
            case "filters" -> filters();
            case "late" -> late();
            case "private" -> {
                try { startService(new Intent().setComponent(new ComponentName("hello.svctarget", "hello.svctarget.PrivateService"))); require(false, "private service started"); }
                catch (SecurityException expected) { System.out.println("[svc] PASS private: " + expected.getMessage()); }
            }
            default -> System.out.println("[svc] unknown scenario");
        }
    }

    private static final int Service_START_STICKY = 1;

    /** One connection bound to two services at once; onBind once per distinct intent; onUnbind returning true gives onRebind for that intent. */
    private void filters() throws Exception {
        require(COUNTER.equals(startService(counter("f"))), "startService");  // keeps the service across its unbinds
        java.util.Map<String, IBinder> got = new java.util.concurrent.ConcurrentHashMap<>();
        CountDownLatch both = new CountDownLatch(2);
        ServiceConnection shared = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { got.put(name.getClassName(), service); both.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        ComponentName echo = new ComponentName("hello.svctarget", "hello.svctarget.EchoService");
        require(bindService(new Intent("a").setComponent(COUNTER), shared, Context.BIND_AUTO_CREATE), "bind a");
        require(bindService(new Intent().setComponent(echo), shared, Context.BIND_AUTO_CREATE), "bind echo");
        require(both.await(15, TimeUnit.SECONDS), "one connection, two services: connected " + got.keySet());
        Object[] a = binding(got.get(COUNTER.getClassName()));
        CountDownLatch other = new CountDownLatch(1);
        IBinder[] b = new IBinder[1];
        ServiceConnection second = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { b[0] = service; other.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        require(bindService(new Intent("b").setComponent(COUNTER), second, Context.BIND_AUTO_CREATE) && other.await(15, TimeUnit.SECONDS), "bind b");
        Object[] bb = binding(b[0]);
        require("a".equals(a[2]) && "b".equals(bb[2]) && (int) bb[0] == 2, "one onBind per intent: a " + java.util.Arrays.toString(a) + " b " + java.util.Arrays.toString(bb));
        unbindService(shared);  // both of its bindings end: intent a has no client left, onUnbind(a) asks for a rebind
        CountDownLatch again = new CountDownLatch(1);
        IBinder[] c = new IBinder[1];
        ServiceConnection third = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) { c[0] = service; again.countDown(); }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        require(bindService(new Intent("a").setComponent(COUNTER), third, Context.BIND_AUTO_CREATE) && again.await(15, TimeUnit.SECONDS), "rebind a");
        Object[] cc = binding(c[0]);
        require("a".equals(cc[2]) && (int) cc[0] == 2 && (int) cc[1] == 1, "rebind of a: " + java.util.Arrays.toString(cc));
        unbindService(second); unbindService(third);
        require(stopService(new Intent().setComponent(COUNTER)), "stopService");
        System.out.println("[svc] PASS filters: one connection bound to two services, one onBind per intent, onRebind after onUnbind returned true");
    }

    /** A package installed while this app and a component host run (the test driver installs hello.svclate on "[svc] INSTALL"): it is known and its service runs. */
    private void late() throws Exception {
        require(COUNTER.equals(startService(counter("l"))), "startService");
        Object[] host = state();  // hello.svctarget's component host runs now
        java.nio.file.Path apps = java.nio.file.Path.of(System.getProperty("apklayer.apps")), info = apps.resolve("hello.svclate/info.properties");
        try (java.nio.file.WatchService watch = apps.getFileSystem().newWatchService()) {
            apps.register(watch, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE);
            System.out.println("[svc] INSTALL hello-svc-late");
            while (!java.nio.file.Files.exists(info)) {  // events, not a poll: the package directory appears, then its info.properties (written last by install)
                if (java.nio.file.Files.isDirectory(info.getParent())) info.getParent().register(watch, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE);  // a rename into the directory is a create
                if (java.nio.file.Files.exists(info)) break;
                java.nio.file.WatchKey key = watch.poll(60, TimeUnit.SECONDS);
                require(key != null, "hello.svclate was not installed");
                key.pollEvents(); key.reset();
            }
        }
        android.content.pm.PackageInfo installed = getPackageManager().getPackageInfo("hello.svclate", android.content.pm.PackageManager.GET_SERVICES);
        require(installed.services != null && installed.services.length == 1, "late package's services " + java.util.Arrays.toString(installed.services));
        ComponentName late = new ComponentName("hello.svclate", "hello.svctarget.CounterService");
        require(late.equals(startService(new Intent().setComponent(late).putExtra("text", "late"))), "startService of the late package");
        Object[] got = state(late);
        require((int) got[0] == 1 && "late".equals(got[1]) && (int) got[3] != (int) host[3], "late package's service " + java.util.Arrays.toString(got) + " host " + java.util.Arrays.toString(host));
        require(stopService(new Intent().setComponent(late)) && stopService(new Intent().setComponent(COUNTER)), "stopService");
        System.out.println("[svc] PASS late: a package installed while a component host ran is known (" + installed.packageName + ") and its service runs in its own process (pid " + got[3] + ")");
    }

    private static Object[] binding(IBinder binder) throws Exception {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try { require(binder.transact(2, data, reply, 0), "transact 2"); return new Object[] {reply.readInt(), reply.readInt(), reply.readString()}; }
        finally { data.recycle(); reply.recycle(); }
    }
}
